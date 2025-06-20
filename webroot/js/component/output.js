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

function highlightJSON(json) {
  if (typeof json !== 'string') {
    json = JSON.stringify(json, null, 2);
  }
  
  return json
    .replace(/("[\w\s_-]+")(\s*:)/g, '<span class="json-key">$1</span>$2')
    .replace(/:\s*(".*?")/g, ': <span class="json-string">$1</span>')
    .replace(/:\s*(\d+\.?\d*)/g, ': <span class="json-number">$1</span>')
    .replace(/:\s*(true|false)/g, ': <span class="json-boolean">$1</span>')
    .replace(/:\s*(null)/g, ': <span class="json-null">$1</span>');
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
  target.innerHTML = highlightJSON(formatOutput(output));
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
      standardOutput.innerHTML = highlightJSON(initialJSON);
    }
  }
}

export { 
  createOutputSection, 
  highlightJSON, 
  displayOutput, 
  displayError, 
  clearError,
  clearOutput,
  initializeOutput,
};