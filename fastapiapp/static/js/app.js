// Fashion Store Assistant JavaScript functionality
document.addEventListener('DOMContentLoaded', function() {
    // Elements
    const productSelect = document.getElementById('sltProduct');
    const queryTextarea = document.getElementById('txtQuery');
    const sendButton = document.getElementById('btnSend');
    const spinner = document.getElementById('spinner');
    const responseElement = document.getElementById('response');
    
    // Load products on page load
    fetch('/api/products')
        .then(response => response.json())
        .then(products => {
            // Clear loading option
            productSelect.innerHTML = '<option value="">Please select...</option>';
            
            // Add each product to the select dropdown
            products.forEach(product => {
                const option = document.createElement('option');
                option.value = product.id;
                option.textContent = product.name;
                productSelect.appendChild(option);
            });
        })
        .catch(error => {
            console.error('Error loading products:', error);
            productSelect.innerHTML = '<option value="">Error loading products</option>';
        });
        
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
        responseElement.textContent = '';
        spinner.style.display = 'flex';
        sendButton.disabled = true;
        
        const eventSource = new EventSource(`/api/reactive-query?product_id=${encodeURIComponent(productId)}&message=${encodeURIComponent(message)}`);
        let fullResponse = '';
        
        // Handle incoming tokens through standard SSE events
        eventSource.onmessage = function(event) {
            // Convert non-breaking spaces to regular spaces before adding to response
            fullResponse += event.data.replace(/\u00A0/g, ' ');
            responseElement.textContent = fullResponse;
            spinner.style.display = 'none';
        };
        
        // Handle completion event 
        eventSource.addEventListener('complete', function() {
            spinner.style.display = 'none';
            sendButton.disabled = false;
            eventSource.close();
        });
        
        // Handle errors
        eventSource.onerror = function(event) {
            console.error('SSE Error:', event);
            // Only show error if we haven't received any response yet
            if (!fullResponse) {
                responseElement.textContent = 'An error occurred while processing your request. Please try again.';
            }
            
            spinner.style.display = 'none';
            sendButton.disabled = false;
            eventSource.close();
        };
    });
});