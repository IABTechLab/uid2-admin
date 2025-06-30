import {generateRoleBadge} from "../roleBadge.js";
import createInputField from "../input/createInputField.js";
import {headingOnlyOperation} from "./headingOnlyOperation.js";
import {operationWithInputs} from "./operationWithInputs.js";

function operation(config) {
    const {
        id,
        title,
        role,
        description = '',
        inputs = []
    } = config;

    const badgeHtml = generateRoleBadge(role);
    const inputsHtml = inputs.length > 0 ?
        `<div class="accordion-inputs">${inputs.map(input => createInputField(input, id)).join('')}</div>` :
        '';

    const hasInputs = inputs.length > 0;
    const hasDescription = description && description.trim() !== '';
    const needsAccordion = hasInputs || hasDescription;

    if (!needsAccordion) {
        return headingOnlyOperation(id, title, badgeHtml);
    }

    return operationWithInputs(title, badgeHtml, description, inputsHtml, id);
}

export {operation};