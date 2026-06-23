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

  // Single-pass tokeniser: quoted-string-followed-by-colon must be tested before
  // bare quoted-string so that keys are distinguished from string values.
  // This prevents colons inside string values (e.g. "azure:eastus2:uuid") from
  // being mis-tokenised by subsequent passes, which was the root cause of a
  // spurious space appearing in link_id values on the service-link admin page.
  return json.replace(
    /("(?:\\.|[^"\\])*")\s*:|(true|false|null)|(-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)|("(?:\\.|[^"\\])*")/g,
    (match, key, keyword, number, string) => {
      if (key !== undefined) {
        return '<span class="json-key">' + key + '</span>' + match.slice(key.length);
      }
      if (keyword === 'true' || keyword === 'false') {
        return '<span class="json-boolean">' + keyword + '</span>';
      }
      if (keyword === 'null') {
        return '<span class="json-null">' + keyword + '</span>';
      }
      if (number !== undefined) {
        return '<span class="json-number">' + number + '</span>';
      }
      if (string !== undefined) {
        return '<span class="json-string">' + string + '</span>';
      }
      return match;
    }
  );
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