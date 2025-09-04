import { test, describe } from 'node:test';
import assert from 'node:assert';
import { highlightJSON } from '../component/output.js';

describe('highlightJSON', () => {
  test('should highlight numbers', () => {
    const input = { count: 42 };
    const result = highlightJSON(JSON.stringify(input));
    
    const expected = '{<span class="json-key">"count"</span>:<span class="json-number">42</span>}';
    assert.strictEqual(result, expected);
  });

  test('should not highlight numbers within strings', () => {
    const input = { 
      link_id: "aws:us-west-2:94889860-0a55-4fbd-981e-3a9ce99ca1ec" 
    };
    const result = highlightJSON(JSON.stringify(input));
    
    const expected = '{<span class="json-key">"link_id"</span>:<span class="json-string">"aws:us-west-2:94889860-0a55-4fbd-981e-3a9ce99ca1ec"</span>}';
    assert.strictEqual(result, expected);
  });

  test('should handle mixed numbers and strings', () => {
    const input = {
      service_id: 8,
      site_id: 276,
      link_id: "aws:us-west-2:94889860-0a55-4fbd-981e-3a9ce99ca1ec"
    };
    const result = highlightJSON(JSON.stringify(input, null, 2));
    
    const expected = `{
  <span class="json-key">"service_id"</span>: <span class="json-number">8</span>,
  <span class="json-key">"site_id"</span>: <span class="json-number">276</span>,
  <span class="json-key">"link_id"</span>: <span class="json-string">"aws:us-west-2:94889860-0a55-4fbd-981e-3a9ce99ca1ec"</span>
}`;
    assert.strictEqual(result, expected);
  });

  test('should highlight strings', () => {
    const input = { name: "test-value" };
    const result = highlightJSON(JSON.stringify(input));
    
    const expected = '{<span class="json-key">"name"</span>:<span class="json-string">"test-value"</span>}';
    assert.strictEqual(result, expected);
  });

  test('should highlight keys', () => {
    const input = { test_key: "value" };
    const result = highlightJSON(JSON.stringify(input));
    
    const expected = '{<span class="json-key">"test_key"</span>:<span class="json-string">"value"</span>}';
    assert.strictEqual(result, expected);
  });

  test('should highlight booleans', () => {
    const input = { flag: true, disabled: false };
    const result = highlightJSON(JSON.stringify(input));
    
    const expected = '{<span class="json-key">"flag"</span>:<span class="json-boolean">true</span>,<span class="json-key">"disabled"</span>:<span class="json-boolean">false</span>}';
    assert.strictEqual(result, expected);
  });

  test('should highlight null', () => {
    const input = { value: null };
    const result = highlightJSON(JSON.stringify(input));
    
    const expected = '{<span class="json-key">"value"</span>:<span class="json-null">null</span>}';
    assert.strictEqual(result, expected);
  });

  test('should handle negative numbers', () => {
    const input = { temperature: -15 };
    const result = highlightJSON(JSON.stringify(input));
    
    const expected = '{<span class="json-key">"temperature"</span>:<span class="json-number">-15</span>}';
    assert.strictEqual(result, expected);
  });

  test('should handle decimal numbers', () => {
    const input = { price: 19.99 };
    const result = highlightJSON(JSON.stringify(input));
    
    const expected = '{<span class="json-key">"price"</span>:<span class="json-number">19.99</span>}';
    assert.strictEqual(result, expected);
  });

  test('should handle scientific notation', () => {
    const input = { value: 1.23e-4 };
    const result = highlightJSON(JSON.stringify(input));
    
    const expected = '{<span class="json-key">"value"</span>:<span class="json-number">0.000123</span>}';
    assert.strictEqual(result, expected);
  });

  test('should handle nested objects', () => {
    const input = {
      user: {
        id: 123,
        name: "John Doe",
        active: true,
        metadata: null,
        scores: [95.5, -2.3, 0]
      }
    };
    const result = highlightJSON(JSON.stringify(input, null, 2));
    
    const expected = `{
  <span class="json-key">"user"</span>: {
    <span class="json-key">"id"</span>: <span class="json-number">123</span>,
    <span class="json-key">"name"</span>: <span class="json-string">"John Doe"</span>,
    <span class="json-key">"active"</span>: <span class="json-boolean">true</span>,
    <span class="json-key">"metadata"</span>: <span class="json-null">null</span>,
    <span class="json-key">"scores"</span>: [
      <span class="json-number">95.5</span>,
      <span class="json-number">-2.3</span>,
      <span class="json-number">0</span>
    ]
  }
}`;
    assert.strictEqual(result, expected);
  });

  test('should handle escaped quotes', () => {
    const input = { 
      message: 'He said "Hello, world!" to everyone' 
    };
    const result = highlightJSON(JSON.stringify(input));
    
    const expected = '{<span class="json-key">"message"</span>:<span class="json-string">"He said \\"Hello, world!\\" to everyone"</span>}';
    assert.strictEqual(result, expected);
  });

  test('should handle empty objects and arrays', () => {
    const input = { empty_obj: {}, empty_array: [] };
    const result = highlightJSON(JSON.stringify(input));
    
    const expected = '{<span class="json-key">"empty_obj"</span>:{},<span class="json-key">"empty_array"</span>:[]}';
    assert.strictEqual(result, expected);
  });

  test('should handle special characters in strings', () => {
    const input = { 
      special: "Line 1\nLine 2\tTabbed\r\nWindows line ending",
      unicode: "Unicode: 🚀 ñ é"
    };
    const result = highlightJSON(JSON.stringify(input));
    
    const expected = '{<span class="json-key">"special"</span>:<span class="json-string">"Line 1\\nLine 2\\tTabbed\\r\\nWindows line ending"</span>,<span class="json-key">"unicode"</span>:<span class="json-string">"Unicode: 🚀 ñ é"</span>}';
    assert.strictEqual(result, expected);
  });

  test('should handle non-object input', () => {
    const input = [1, 2, 3];
    const result = highlightJSON(JSON.stringify(input));
    
    const expected = '[<span class="json-number">1</span>,<span class="json-number">2</span>,<span class="json-number">3</span>]';
    assert.strictEqual(result, expected);
  });
});
