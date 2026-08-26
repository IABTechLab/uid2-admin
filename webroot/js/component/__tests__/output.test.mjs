import { highlightJSON } from '../output.js';

// Helper: strip all HTML tags to get the visible text only
const stripTags = html => html.replace(/<[^>]+>/g, '');

// Helper: extract all span class values from the highlighted output
const spansOf = (cls, html) => {
  const re = new RegExp(`<span class="${cls}">(.*?)<\\/span>`, 'gs');
  return [...html.matchAll(re)].map(m => m[1]);
};

describe('highlightJSON', () => {
  describe('string values with embedded colons (regression for UID2-7159)', () => {
    it('does not introduce a spurious space in a link_id containing colons', () => {
      const input = { link_id: 'azure:eastus2:71ad8e1e-aabb-ccdd-eeff-001122334455' };
      const result = highlightJSON(input);
      const visible = stripTags(result);
      expect(visible).toContain('"azure:eastus2:71ad8e1e-aabb-ccdd-eeff-001122334455"');
      expect(visible).not.toContain('eastus2: 71');
    });

    it('wraps a link_id value in json-string, not json-number', () => {
      const input = { link_id: 'azure:eastus2:71ad8e1e-aabb-ccdd-eeff-001122334455' };
      const result = highlightJSON(input);
      expect(spansOf('json-string', result)).toContain('"azure:eastus2:71ad8e1e-aabb-ccdd-eeff-001122334455"');
      expect(spansOf('json-number', result)).toEqual([]);
    });

    it('does not split a string value at an embedded colon', () => {
      const input = { key: 'prefix:suffix' };
      const result = highlightJSON(input);
      expect(spansOf('json-string', result)).toContain('"prefix:suffix"');
    });

    it('handles multiple colons inside a string value', () => {
      const input = { endpoint: 'https://example.com:8080/path' };
      const result = highlightJSON(input);
      expect(spansOf('json-string', result)).toContain('"https://example.com:8080/path"');
    });
  });

  describe('key highlighting', () => {
    it('wraps object keys in json-key spans', () => {
      const result = highlightJSON({ my_key: 'value' });
      expect(spansOf('json-key', result)).toContain('"my_key"');
    });

    it('highlights keys that contain hyphens and underscores', () => {
      const result = highlightJSON({ 'link-id': 'v', link_id: 'v' });
      const keys = spansOf('json-key', result);
      expect(keys).toContain('"link-id"');
      expect(keys).toContain('"link_id"');
    });
  });

  describe('primitive value highlighting', () => {
    it('wraps integer values in json-number spans', () => {
      const result = highlightJSON({ count: 42 });
      expect(spansOf('json-number', result)).toContain('42');
    });

    it('wraps float values in json-number spans', () => {
      const result = highlightJSON({ ratio: 3.14 });
      expect(spansOf('json-number', result)).toContain('3.14');
    });

    it('wraps boolean true in json-boolean spans', () => {
      const result = highlightJSON({ active: true });
      expect(spansOf('json-boolean', result)).toContain('true');
    });

    it('wraps boolean false in json-boolean spans', () => {
      const result = highlightJSON({ active: false });
      expect(spansOf('json-boolean', result)).toContain('false');
    });

    it('wraps null in json-null spans', () => {
      const result = highlightJSON({ value: null });
      expect(spansOf('json-null', result)).toContain('null');
    });
  });

  describe('string value highlighting', () => {
    it('wraps plain string values in json-string spans', () => {
      const result = highlightJSON({ name: 'Alice' });
      expect(spansOf('json-string', result)).toContain('"Alice"');
    });

    it('wraps array string elements in json-string spans', () => {
      const result = highlightJSON({ roles: ['MAPPER', 'ID_READER'] });
      const strings = spansOf('json-string', result);
      expect(strings).toContain('"MAPPER"');
      expect(strings).toContain('"ID_READER"');
    });

    it('does not highlight "true" or "false" inside a string value as boolean', () => {
      const result = highlightJSON({ flag: 'this is true and false' });
      expect(spansOf('json-string', result)).toContain('"this is true and false"');
      expect(spansOf('json-boolean', result)).toEqual([]);
    });

    it('does not highlight digits inside a string value as numbers', () => {
      const result = highlightJSON({ id: 'ref:42:end' });
      expect(spansOf('json-string', result)).toContain('"ref:42:end"');
      expect(spansOf('json-number', result)).toEqual([]);
    });
  });

  describe('visible text fidelity', () => {
    it('preserves all original values when HTML is stripped', () => {
      const input = {
        link_id: 'azure:eastus2:71ad8e1e-aabb-ccdd-eeff-001122334455',
        service_id: 3,
        name: 'Azure East US 2',
        disabled: false,
        config: null,
      };
      const result = highlightJSON(input);
      const visible = stripTags(result);
      expect(visible).toContain('"azure:eastus2:71ad8e1e-aabb-ccdd-eeff-001122334455"');
      expect(visible).toContain('3');
      expect(visible).toContain('"Azure East US 2"');
      expect(visible).toContain('false');
      expect(visible).toContain('null');
    });

    it('accepts a pre-serialised JSON string', () => {
      const json = JSON.stringify({ x: 1 }, null, 2);
      const result = highlightJSON(json);
      expect(stripTags(result)).toContain('"x"');
      expect(stripTags(result)).toContain('1');
    });
  });
});
