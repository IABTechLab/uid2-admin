function doApiCall(method, url, outputDiv, errorDiv, body) {
    $(outputDiv).html('');
    $(errorDiv).html('');

    authHeader = "Bearer " + window.__uid2_admin_token;

    $.ajax({
        type: method,
        url: url,
        dataType: 'text',
        headers: {
            "Authorization": authHeader
        },
        data : body,
        success: function (text) {
            var pretty = JSON.stringify(JSON.parse(text),null,2);
            $(outputDiv).html(pretty);
        },
        error: function (err) {
            $(errorDiv).html('Error: ' + err.status + ': ' + err.statusText);
        }
    });
}

function doApiCallWithCallback(method, url, onSuccess, onError, body) {
    authHeader = "Bearer " + window.__uid2_admin_token;

    $.ajax({
        type: method,
        url: url,
        dataType: 'text',
        headers: {
            "Authorization": authHeader
        },
        data : body,
        success: function (text) {
            onSuccess(text);
        },
        error: function (err) {
            onError(err);
        }
    });
}

function init() {
    $.ajax({
        type: 'GET',
        url: '/api/token/get',
        dataType: 'text',
        success: function (text) {
            var u = JSON.parse(text);
            $('#loginEmail').html(u.contact);
            $('.authed').show();
            if (u.roles.findIndex(e => e === 'CLIENTKEY_ISSUER') >= 0) {
                $('.ro-cki').show();
            }
            if (u.roles.findIndex(e => e === 'OPERATOR_MANAGER') >= 0) {
                $('.ro-opm').show();
            }
            if (u.roles.findIndex(e => e === 'ADMINISTRATOR') >= 0) {
                $('.ro-adm').show();
            }
            if (u.roles.findIndex(e => e === 'SECRET_MANAGER') >= 0) {
                $('.ro-sem').show();
            }
            if (u.roles.length === 0) {
                $('.ro-nil').show();
            }

            window.__uid2_admin_user = u
            window.__uid2_admin_token = window.__uid2_admin_user.key;
        },
        error: function (err) {
            // alert('Error: ' + err.status + ': ' + err.statusText);
            $('.notauthed').show();
        }
    });

    $(document).ready(function () {
        if (window.location.origin.endsWith('.eu')) {
            let header = $("h1").first();
            header.text(header.text().replace("UID2", "EUID"));
        }
    });
}

init();