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
        error: function (err) { standardErrorCallback(err, outputDiv) }
    });
}
function doApiCallWithBody(method, url, body, outputDiv, errorDiv) {
    $(outputDiv).html('');
    $(errorDiv).html('');

    authHeader = "Bearer " + window.__uid2_admin_token;

    $.ajax({
        type: method,
        url: url,
        data: body,
        dataType: 'text',
        headers: {
            "Authorization": authHeader
        },
        success: function (text) {
            var pretty = JSON.stringify(JSON.parse(text),null,2);
            $(outputDiv).html(pretty);
        },
        error: function (err) { standardErrorCallback(err, outputDiv) }
    });
}

function errorCallback(err) { standardErrorCallback(err, '#errorOutput') }

function standardErrorCallback(err, errorDiv) {
    $(errorDiv).html('Error: ' + err.status + ': ' + (isJsonString(err.responseText) ? JSON.parse(err.responseText).message : (err.responseText ? err.responseText : err.statusText)));
}

function isJsonString(str) {
    try {
        JSON.parse(str);
    } catch (e) {
        return false;
    }
    return true;
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
            // alert('Error: ' + err.status + ': ' + JSON.parse(err).message);
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
