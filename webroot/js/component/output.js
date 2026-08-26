function createOutputSection() {
  return `
    <div class="output-container">
      <h3>Output</h3>
      <div id="output">
        <pre id="errorOutput"></pre>
        <pre id="standardOutput">Nothing yet</pre>
      </div>
    </div>
  `;
}


function formatOutput(data) {
  if (typeof data === 'string') {
    try {
      // Try to parse as JSON
      return JSON.stringify(JSON.parse(data), null, 2);
    } catch (e) {
      // If not JSON, use as string
      return data;
    }
  } else {
    return JSON.stringify(data, null, 2);
  }
}

function displayOutput(output) {
  const target = document.querySelector('#standardOutput');
  if (!target) return;
  target.textContent = formatOutput(output);
}

function clearOutput() {
  const target = document.querySelector('#standardOutput');
  target.innerHTML = '';
}

function displayError(message) {
  const target = document.querySelector('#errorOutput');
  if (!target) return;
  
  target.textContent = message;
  target.style.display = 'block';
}

function clearError() {
  const target = document.querySelector('#errorOutput');
  if (target) {
    target.style.display = 'none';
    target.innerHTML = '';
  }
}

function initializeOutput() {
  const outputContainer = document.querySelector('.output-container');
  if (outputContainer) {
    outputContainer.outerHTML = createOutputSection();
  }

  const standardOutput = document.getElementById('standardOutput');
  if (standardOutput) {
    const initialJSON = standardOutput.textContent;
    if (initialJSON.trim()) {
      standardOutput.textContent = initialJSON;
    }
  }
}

export { 
  createOutputSection, 
  displayOutput, 
  displayError, 
  clearError,
  clearOutput,
  initializeOutput,
};