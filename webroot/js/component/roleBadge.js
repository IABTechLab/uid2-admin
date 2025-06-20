import { initializeTooltips } from './tooltip.js';

function getRoleBadgeConfig(role) {
    const configs = {
        maintainer: {
            text: 'Maintainer',
            tooltip: 'A member of the developer group in Okta. Click to view members.'
        },
        elevated: {
            text: 'Elevated',
            tooltip: 'Requires raising developer-elevated-request in Okta configuration GitHub repository. Click to go to the workflow.'
        },
        superuser: {
            text: 'Superuser',
            tooltip: 'Only accessible by members of admin group in Okta. Click to view members.'
        }
    };
    return configs[role] || configs.maintainer;
}

function generateRoleBadge(role) {
    const {text, tooltip} = getRoleBadgeConfig(role);
    return `<span class="permission-badge ${role}" data-tooltip="${(tooltip)}">${(text)}</span>`;
}

function initializeRoleBadges() {
    // Initialize tooltips for role badges
    initializeTooltips('.permission-badge');
    
    // Handle click events for role badges
    document.addEventListener('click', function (e) {
        if (e.target && e.target.classList && e.target.classList.contains('permission-badge')) {
            if (e.target.classList.contains('maintainer')) {
                window.open('https://github.com/UnifiedID2/uid2-okta-configuration/blob/main/groups/groups.yaml#L19', '_blank');
            } else if (e.target.classList.contains('elevated')) {
                window.open('https://github.com/UnifiedID2/uid2-okta-configuration/actions/workflows/request-elevated.yml', '_blank');
            } else if (e.target.classList.contains('superuser')) {
                window.open('https://github.com/UnifiedID2/uid2-okta-configuration/blob/main/groups/groups.yaml#L2', '_blank');
            }
        }
    });
}

export { generateRoleBadge, initializeRoleBadges };