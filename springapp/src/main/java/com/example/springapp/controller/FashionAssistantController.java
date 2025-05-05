package com.example.springapp.controller;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.springapp.model.Product;
import com.example.springapp.service.SLMService;

@Controller
public class FashionAssistantController {

    @Autowired
    private SLMService slmService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("products", Product.getAllProducts());
        return "index";
    }
    
    // Support both GET and POST requests for the query endpoint
    @GetMapping("/query")
    public SseEmitter processQueryGet(
            @RequestParam("productId") int productId,
            @RequestParam("message") String message) {
        return handleQuery(productId, message);
    }
    
    @PostMapping("/query")
    public SseEmitter processQueryPost(
            @RequestParam("productId") int productId,
            @RequestParam("message") String message) {
        return handleQuery(productId, message);
    }
    
    // Common method to handle both GET and POST requests
    private SseEmitter handleQuery(int productId, String message) {
        // Increase timeout to 10 minutes (600,000 ms)
        SseEmitter emitter = new SseEmitter(600000L);
        
        Optional<Product> productOpt = Product.getProductById(productId);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            
            // Create JSON payload similar to the .NET version
            JSONObject queryData = new JSONObject();
            queryData.put("user_message", message);
            queryData.put("product_name", product.getName());
            queryData.put("product_description", product.getDescription());
            
            String prompt = queryData.toString();
            
            // Track if we've received the final token
            AtomicBoolean responseCompleted = new AtomicBoolean(false);
            
            // Stream response from language model
            slmService.streamChatCompletionsAsync(prompt, token -> {
                try {
                    // Ensure spaces are preserved by replacing them with Unicode non-breaking spaces
                    String preservedToken = token.replace(" ", "\u00A0");
                    
                    emitter.send(SseEmitter.event()
                            .name("token")
                            .data(preservedToken));
                } catch (Exception e) {
                    System.err.println("Error sending token: " + e.getMessage());
                    emitter.completeWithError(e);
                }
            }, () -> {
                // This callback will be called when the model has finished generating
                try {
                    // Send a completion event to let the client know we're done
                    emitter.send(SseEmitter.event()
                            .name("complete")
                            .data("Generation complete"));
                    
                    // Mark as complete so our background thread knows not to timeout
                    responseCompleted.set(true);
                    
                    // Complete the emitter after a short delay
                    Thread.sleep(1000);
                    emitter.complete();
                } catch (Exception e) {
                    System.err.println("Error completing SSE: " + e.getMessage());
                    emitter.completeWithError(e);
                }
            });
            
            // Safety net: Complete the emitter after a long timeout if the model never finishes
            new Thread(() -> {
                try {
                    // Wait for 3 minutes max
                    for (int i = 0; i < 36; i++) {
                        Thread.sleep(5000); // Check every 5 seconds
                        if (responseCompleted.get()) {
                            // Model finished normally, our work here is done
                            return;
                        }
                    }
                    
                    // If we get here, the model didn't finish in 3 minutes
                    // Send a timeout event and complete the emitter
                    emitter.send(SseEmitter.event()
                            .name("timeout")
                            .data("Response generation timeout"));
                    emitter.complete();
                } catch (Exception e) {
                    // Only complete with error if not already completed
                    if (!responseCompleted.get()) {
                        emitter.completeWithError(e);
                    }
                }
            }).start();
        } else {
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("Product not found"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }
        
        return emitter;
    }
}