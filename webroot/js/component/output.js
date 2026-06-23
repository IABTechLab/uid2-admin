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
  
  // Common regex patterns for JSON syntax highlighting
  
  // Matches optional whitespace (spaces, tabs, newlines)
  // Examples: "", " ", "\t", "\n", "  \n  "
  const optionalWhitespace = '\\s*';
  
  // Matches JSON characters that precede a value: colon followed by optional whitespace, or array/object start with optional whitespace
  // Examples: ": ", ":[", ", ", "[ ", "[42"
  const valuePrefixes = '(:\\s*|[\\[\\,]\\s*)';
  
  // Matches JSON characters that follow a value: comma, closing brackets/braces, newline, or end of string
  // Examples: ",", "]", "}", "\n", end of string
  const valueEndings = '(?=\\s*[,\\]\\}\\n]|$)';
  
  // Matches quoted strings with escaped characters
  // Examples: "hello", "world \"quoted\"", "path\\to\\file", "unicode: \\u0041"
  const quotedString = '"(?:[^"\\\\]|\\\\.)*"';
  
  // Matches JSON object keys (quoted strings containing word chars, spaces, underscores, hyphens)
  // Examples: "name", "user_id", "first-name", "my key", "data_2023"
  const objectKey = '"[\\w\\s_-]+"';
  
  // Matches numbers (integer, decimal, scientific notation)
  // Examples: 42, -17, 3.14, -0.5, 1e10, 2.5e-3, 1E+5
  const number = '-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?';
  
  // Complete regex patterns for each replacement
  const keyPattern = new RegExp(`(${objectKey})(${optionalWhitespace}:)`, 'g');        // "name": or "user_id" :
  const stringValuePattern = new RegExp(`(:\\s*)(${quotedString})`, 'g');              // : "John Doe" or :"hello"
  const numberPattern = new RegExp(`${valuePrefixes}(${number})${valueEndings}`, 'g'); // : 42, [123, or , -3.14
  const booleanPattern = new RegExp(`${valuePrefixes}(true|false)${valueEndings}`, 'g'); // : true, [false, or , true
  const nullPattern = new RegExp(`${valuePrefixes}(null)${valueEndings}`, 'g');        // : null, [null, or , null
  
  return json
    .replace(keyPattern, '<span class="json-key">$1</span>$2')
    .replace(stringValuePattern, '$1<span class="json-string">$2</span>')
    .replace(numberPattern, '$1<span class="json-number">$2</span>')
    .replace(booleanPattern, '$1<span class="json-boolean">$2</span>')
    .replace(nullPattern, '$1<span class="json-null">$2</span>');
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