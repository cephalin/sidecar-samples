// Fashion Store Assistant JavaScript functionality
document.addEventListener('DOMContentLoaded', function() {
    // Elements
    const productSelect = document.getElementById('sltProduct');
    const queryTextarea = document.getElementById('txtQuery');
    const sendButton = document.getElementById('btnSend');
    const spinner = document.getElementById('spinner');
    const responseElement = document.getElementById('response');
    
    // Event listener for the send button
    sendButton.addEventListener('click', function() {
        // Validation
        const productId = productSelect.value;
        const message = queryTextarea.value.trim();
        
        if (!productId) {
            alert('Please select a product');
            return;
        }
        
        if (!message) {
            alert('Please enter a question');
            return;
        }
        
        // Reset and show spinner
        responseElement.innerHTML = '';
        spinner.style.display = 'flex';
        sendButton.disabled = true;
        
        // Track if we've already handled connection closure
        let connectionHandled = false;
        
        // Set up SSE connection for streaming response
        const queryUrl = `/query?productId=${encodeURIComponent(productId)}&message=${encodeURIComponent(message)}`;
        const eventSource = new EventSource(queryUrl);
        let fullResponse = '';
        
        // Handle incoming tokens
        eventSource.addEventListener('token', function(event) {
            fullResponse += event.data;
            responseElement.innerHTML = fullResponse;
            spinner.style.display = 'none';
        });
        
        // Handle standard message events
        eventSource.onmessage = function(event) {
            fullResponse += event.data;
            responseElement.innerHTML = fullResponse;
            spinner.style.display = 'none';
        };
        
        // Handle explicit completion event from server
        eventSource.addEventListener('complete', function(event) {
            if (!connectionHandled) {
                connectionHandled = true;
                console.log('Server signaled completion');
                sendButton.disabled = false;
                eventSource.close();
            }
        });
        
        // Handle timeout event from server
        eventSource.addEventListener('timeout', function(event) {
            if (!connectionHandled) {
                connectionHandled = true;
                console.log('Server signaled timeout');
                fullResponse += "\n\n(Response generation timed out)";
                responseElement.innerHTML = fullResponse;
                sendButton.disabled = false;
                eventSource.close();
            }
        });
        
        // Handle errors
        eventSource.addEventListener('error', function(event) {
            if (!connectionHandled) {
                connectionHandled = true;
                
                // Only show error if we haven't received any response yet
                if (!fullResponse) {
                    responseElement.textContent = 'An error occurred while processing your request. Please try again.';
                }
                
                spinner.style.display = 'none';
                sendButton.disabled = false;
                eventSource.close();
            }
        });
        
        // No auto-close timeout - rely on server events instead
    });
});
