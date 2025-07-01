class HttpClient {
  async makeRequest(url, options = {}) {
    const response = await fetch(url, options);
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    
    return this.parseResponse(response);
  }

  async parseResponse(response) {
    const contentType = response.headers.get('content-type');
    const responseText = await response.text();
    
    if (responseText.trim() === '') {
      return { message: 'Operation successful, no text in response' };
    }
    
    // Always try to parse as JSON first, regardless of content-type
    try {
      return JSON.parse(responseText);
    } catch (jsonError) {
      // If JSON parsing fails, return as plain text message
      return { message: responseText };
    }
  }

  async get(url) {
    return this.makeRequest(url, {
      method: 'GET'
    });
  }

  async post(url, payload = null) {
    const options = {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      }
    };

    if (payload) {
      options.body = typeof payload === 'string' ? payload : JSON.stringify(payload);
    }

    return this.makeRequest(url, options);
  }

  async put(url, payload = null) {
    const options = {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      }
    };

    if (payload) {
      options.body = typeof payload === 'string' ? payload : JSON.stringify(payload);
    }

    return this.makeRequest(url, options);
  }

  async delete(url) {
    return this.makeRequest(url, {
      method: 'DELETE'
    });
  }

  async patch(url, payload = null) {
    const options = {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      }
    };

    if (payload) {
      options.body = typeof payload === 'string' ? payload : JSON.stringify(payload);
    }

    return this.makeRequest(url, options);
  }

  async request(url, method, payload = null) {
    if (method === 'GET') {
      return httpClient.get(url);
    } else if (method === 'POST') {
      return httpClient.post(url, payload);
    } else if (method === 'PUT') {
      return httpClient.put(url, payload);
    } else if (method === 'DELETE') {
      return httpClient.delete(url);
    } else if (method === 'PATCH') {
      return httpClient.patch(url, payload);
    } else {
      throw new Error(`Unsupported HTTP method: ${method}`);
    }
  }
}

export const httpClient = new HttpClient();