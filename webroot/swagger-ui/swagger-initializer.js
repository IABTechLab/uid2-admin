window.onload = function() {
  //<editor-fold desc="Changeable Configuration Block">
    console.log('Loading window1'); // remove debug log
  // the following lines will be replaced by docker/configurator, when it runs in a docker-container
  window.ui = SwaggerUIBundle({
    url: "swagger.json",
    dom_id: '#swagger-ui',
    deepLinking: true,
    presets: [
      SwaggerUIBundle.presets.apis,
      SwaggerUIStandalonePreset
    ],
//    plugins: [
//      SwaggerUIBundle.plugins.DownloadUrl,
//      // Add the Preauthorize plugin to automatically fetch the bearer token
//      (system) => {
//        return {
//          statePlugins: {
//            auth: {
//              wrapActions: {
//                authorize: (oriAction, system) => async (args) => {
//                  // Fetch your bearer token here
////                  await init();
//                    console.log('In authorize:');
//                    const token = await fetchBearerToken();
//                    console.log('Bearer Token:', token);
//                  // Call the original authorize action with the fetched token
//                  return oriAction({ ...args, token });
//                },
//              },
//              selectors: {
//                // Customize if needed
//                getToken: (state) => () => state.auth.get('token'),
//              },
//            },
//          },
//        };
//      },
//    ],
    layout: "StandaloneLayout"
//    onComplete: async function() {
//        try {
//            // Default Bearer token
//            console.log('onComplete');
//            const token = 'Bearer ' + fetchBearerToken();
//            console.log('Bearer Token:', token);
//            window.ui.preauthorizeApiKey("bearerAuth", token);
//        } catch {
//            console.error('Error in onComplete:', error.message);
//        }
//      }
  });

  //</editor-fold>
};

async function fetchBearerToken() {
  try {
    console.log('In fetchBearerToken');
    // Make an HTTP GET request to '/api/token/get'
    const response = await fetch('/api/token/get', {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        // Add any additional headers if needed
      },
    });

    // Check if the response is successful (status code 200)
    if (!response.ok) {
      throw new Error(`Failed to fetch token: ${response.status}`);
    }

    // Parse the JSON response
    const userData = await response.json();

    // Extract the key from the user data
    const key = userData.key;

    return key;
  } catch (error) {
    // Handle errors (e.g., network issues, server errors)
    console.error('Error fetching token:', error.message);
    throw error; // Propagate the error to the calling code if needed
  }
}