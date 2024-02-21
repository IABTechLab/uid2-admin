function doApiCall(method, url, outputDiv, errorDiv, body) {
    $(outputDiv).text('');
    $(errorDiv).text('');

    authHeader = "Bearer " + window.__uid2_admin_token;

    $.ajax({
        type: method,
        url: url,
        dataType: 'text',
        data : body,
        success: function (text) {
            var pretty = JSON.stringify(JSON.parse(text),null,2);
            $(outputDiv).text(pretty);
        },
        error: function (err, status, header) {
            if(err.getResponseHeader("REQUIRES_AUTH") == 1) {
                $('body').hide()
                $('body').replaceWith("Unauthorized, prompting reauthentication...")
                $('body').show()
                $(function () {
                    setTimeout(function() {
                        window.location.replace("/login");
                    }, 3000);
                });
            } else {
                standardErrorCallback(err, errorDiv)
            }
        }
    });
}
function doApiCallWithBody(method, url, body, outputDiv, errorDiv) {
    $(outputDiv).text('');
    $(errorDiv).text('');

    authHeader = "Bearer " + window.__uid2_admin_token;

    $.ajax({
        type: method,
        url: url,
        data: body,
        dataType: 'text',
        success: function (text) {
            var pretty = JSON.stringify(JSON.parse(text),null,2);
            $(outputDiv).text(pretty);
        },
        error: function (err) {
            if(err.getResponseHeader("REQUIRES_AUTH") == 1) {
                $('body').hide()
                $('body').replaceWith("Unauthorized, prompting reauthentication...")
                $('body').show()
                $(function () {
                    setTimeout(function() {
                        window.location.replace("/login");
                    }, 3000);
                });
            } else {
                standardErrorCallback(err, errorDiv)
            }
        }
    });
}

function errorCallback(err) { standardErrorCallback(err, '#errorOutput') }

function standardErrorCallback(err, errorDiv) {
    $(errorDiv).text('Error: ' + err.status + ': ' + (isJsonString(err.responseText) ? JSON.parse(err.responseText).message : (err.responseText ? err.responseText : err.statusText)));
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
        data : body,
        success: function (text) {
            onSuccess(text);
        },
        error: function (err) {
            if(err.getResponseHeader("REQUIRES_AUTH") == 1) {
                $('body').hide()
                $('body').replaceWith("Unauthorized, prompting reauthentication...")
                $('body').show()
                $(function () {
                    setTimeout(function() {
                        window.location.replace("/login");
                    }, 3000);
                });
            } else {
                onError(err);
            }
        }
    });
}

function init() {
    $.ajax({
        type: 'GET',
        url: '/api/userinfo',
        dataType: 'text',
        success: function (text) {
            var u = JSON.parse(text);
            $('#loginEmail').text(u.email);
            $('.authed').show();
            if (u.groups.findIndex(e => e === 'developer' || e === 'developer-elevated' || e === 'infra-admin' || e === 'admin') >= 0) {
                $('.ro-cki').show();
            }
            if (u.groups.findIndex(e => e === 'developer' || e === 'developer-elevated' || e === 'infra-admin' || e === 'admin') >= 0) {
                $('.ro-opm').show();
            }
            if (u.groups.findIndex(e => e === 'developer' || e === 'developer-elevated' || e === 'infra-admin' || e === 'admin') >= 0) {
                $('.ro-adm').show();
            }
            if (u.groups.findIndex(e => e === 'developer' || e === 'developer-elevated' || e === 'infra-admin' || e === 'admin') >= 0) {
                $('.ro-sem').show();
            }
            if (u.groups.length === 0) {
                $('.ro-nil').show();
            }
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
