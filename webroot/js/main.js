function doApiCall(method, url, outputDiv, errorDiv, body) {
    $(outputDiv).text("");
    $(errorDiv).text("");

    $.ajax({
        type: method,
        url: url,
        dataType: "text",
        data : body,
        success: function (text) {
            const pretty = JSON.stringify(JSON.parse(text),null,2);
            $(outputDiv).text(pretty);
        },
        error: function (err) {
            standardErrorCallback(err, errorDiv)
        }
    });
}

function doApiCallWithCallback(method, url, onSuccess, onError, body) {
    $.ajax({
        type: method,
        url: url,
        dataType: "text",
        data : body,
        success: function (text) {
            onSuccess(text);
        },
        error: function (err) {
            standardErrorCallback(err, undefined, onError);
        }
    });
}

function errorCallback(err) { standardErrorCallback(err, "#errorOutput") }

function standardErrorCallback(err, errorDiv, onError) {
    if(err.getResponseHeader("REQUIRES_AUTH") == 1) {
        $("body").hide()
        $("body").replaceWith("Unauthorized, prompting reauthentication...")
        $("body").show()
        $(function () {
            setTimeout(function() {
                window.location.replace("/login");
            }, 3000);
        });
    } else {
        if(onError === undefined) {
            $(errorDiv).text("Error: " + err.status + ": " + (isJsonString(err.responseText) ? JSON.parse(err.responseText).message : (err.responseText ? err.responseText : err.statusText)));
        } else {
            onError(err);
        }
    }
}

function isJsonString(str) {
    try {
        JSON.parse(str);
    } catch (e) {
        return false;
    }
    return true;
}

function init() {
    $.ajax({
        type: "GET",
        url: "/api/userinfo",
        dataType: "text",
        success: function (text) {
            var u = JSON.parse(text);
            $("#loginEmail").text(u.email);
            $(".authed").show();
            if (u.groups.findIndex(e => e === "developer" || e === "developer-elevated" || e === "infra-admin" || e === "admin") >= 0) {
                $(".ro-cki").show();
                $(".ro-opm").show();
                $(".ro-adm").show();
                $(".ro-sem").show();
            }
            if (u.groups.length === 0) {
                $(".ro-nil").show();
            }
        },
        error: function (err) {
            // alert("Error: " + err.status + ": " + JSON.parse(err).message);
            $(".notauthed").show();
        }
    });

    $(document).ready(function () {
        if (window.location.origin.endsWith(".eu")) {
            let header = $("h1").first();
            header.text(header.text().replace("UID2", "EUID"));
        }

        if (window.location.origin.includes("integ")) {
            let header = $("h1").first();
            header.text(header.text() + " - Integ");
        } else if (window.location.origin.includes("prod")) {
            let header = $("h1").first();
            header.text(header.text() + " - Prod");
        }
    });
}

init();