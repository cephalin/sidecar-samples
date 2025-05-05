package com.example.springapp.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SLMService {

    @Value("${fashion.assistant.api.url}")
    private String apiUrl;

    // Original method - kept for backward compatibility
    public void streamChatCompletionsAsync(String prompt, Consumer<String> tokenConsumer) {
        streamChatCompletionsAsync(prompt, tokenConsumer, () -> {});
    }

    // Updated method with completion callback
    public void streamChatCompletionsAsync(String prompt, Consumer<String> tokenConsumer, Runnable onComplete) {
        Thread streamThread = new Thread(() -> {
            try {
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);
                
                // Build the request JSON
                JSONObject requestJson = new JSONObject();
                JSONArray messages = new JSONArray();
                
                JSONObject systemMessage = new JSONObject();
                systemMessage.put("role", "system");
                systemMessage.put("content", "You are a helpful assistant.");
                messages.put(systemMessage);
                
                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", prompt);
                messages.put(userMessage);
                
                requestJson.put("messages", messages);
                requestJson.put("stream", true);
                requestJson.put("cache_prompt", false);
                requestJson.put("n_predict", 2048); // Increased from 150 to 2048 tokens
                
                // Send the request
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestJson.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                // Process the response
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            line = line.substring(6).trim();
                            if (line.equals("[DONE]")) {
                                // Model has finished generating
                                break;
                            } else {
                                try {
                                    JSONObject jsonResponse = new JSONObject(line);
                                    JSONArray choices = jsonResponse.getJSONArray("choices");
                                    if (choices.length() > 0) {
                                        JSONObject choice = choices.getJSONObject(0);
                                        JSONObject delta = choice.getJSONObject("delta");
                                        if (delta.has("content")) {
                                            String content = delta.getString("content");
                                            if (!content.isEmpty()) {
                                                tokenConsumer.accept(content);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    // Skip invalid JSON
                                }
                            }
                        }
                    }
                    
                    // Signal completion after we've processed all tokens
                    onComplete.run();
                }
                
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        
        streamThread.start();
    }
}