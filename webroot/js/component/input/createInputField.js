import {multilineInput} from "./multilineInput.js";
import {checkboxInput} from "./checkboxInput.js";
import {selectInput} from "./selectInput.js";
import {multiSelectInput} from "./multiSelectInput.js";
import {textInput} from "./textInput.js";

function createInputField(input, operationId) {
    const {
        name,
        label,
        type = 'text',
        placeholder = '',
        required = false,
        defaultValue = '',
        size = 1,
        options = []
    } = input;
    const id = `${operationId}-${name}`;
    const gridSpan = size > 1 ? size : 0;
    const gridStyle = gridSpan > 0 ? ` style="grid-column: span ${gridSpan};"` : '';

    if (type === 'multi-line') {
        return multilineInput(id, label, required, name, placeholder, defaultValue);
    }

    if (type === 'checkbox') {
        return checkboxInput(defaultValue, gridStyle, id, label, required, name);
    }

    if (type === 'select') {
        return selectInput(options, defaultValue, gridStyle, id, label, required, name);
    }

    if (type === 'multi-select') {
        return multiSelectInput(options, id, defaultValue, name, label, required);
    }

    return textInput(gridStyle, id, label, required, type, name, placeholder, defaultValue);
}

export default createInputField;