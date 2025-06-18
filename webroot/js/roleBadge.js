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

    document.addEventListener('mouseenter', function (e) {
        if (e.target && e.target.classList && e.target.classList.contains('permission-badge')) {
            const tooltipText = e.target.getAttribute('data-tooltip');
            if (tooltipText) {
                const tooltip = document.createElement('div');
                tooltip.className = 'tooltip';
                tooltip.textContent = tooltipText;
                document.body.appendChild(tooltip);

                const rect = e.target.getBoundingClientRect();
                const tooltipRect = tooltip.getBoundingClientRect();
                const windowWidth = window.innerWidth;
                const windowHeight = window.innerHeight;

                let top = rect.top - tooltipRect.height - 10;
                let left = rect.left + (rect.width / 2) - (tooltipRect.width / 2);

                if (left < 10) {
                    left = 10;
                } else if (left + tooltipRect.width > windowWidth - 10) {
                    left = windowWidth - tooltipRect.width - 10;
                }

                if (top < 10) {
                    top = rect.bottom + 10;
                }

                tooltip.style.top = top + 'px';
                tooltip.style.left = left + 'px';
            }
        }
    }, true);

    document.addEventListener('mouseleave', function (e) {
        if (e.target && e.target.classList && e.target.classList.contains('permission-badge')) {
            const tooltips = document.querySelectorAll('.tooltip');
            tooltips.forEach(tooltip => tooltip.remove());
        }
    }, true);
}

export { generateRoleBadge, initializeRoleBadges };