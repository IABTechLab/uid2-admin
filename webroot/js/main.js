function doApiCall(method, url, outputDiv, errorDiv, body) {
    $(outputDiv).text("");
    $(errorDiv).text("");

    authHeader = "Bearer " + window.__uid2_admin_token;

    $.ajax({
        type: method,
        url: url,
        dataType: "text",
        headers: {
            "Authorization": authHeader
        },
        data : body,
        success: text => setOutput(text, outputDiv),
        error: err => standardErrorCallback(err, errorDiv)
    });
}

function doApiCallWithBody(method, url, body, outputDiv, errorDiv) {
    $(outputDiv).text("");
    $(errorDiv).text("");

    authHeader = "Bearer " + window.__uid2_admin_token;

    $.ajax({
        type: method,
        url: url,
        data: body,
        dataType: "text",
        headers: {
            "Authorization": authHeader
        },
        success: text => setOutput(text, outputDiv),
        error: err => standardErrorCallback(err, errorDiv)
    });
}

function doApiCallWithCallback(method, url, onSuccess, onError, body) {
    authHeader = "Bearer " + window.__uid2_admin_token;

    $.ajax({
        type: method,
        url: url,
        dataType: "text",
        headers: {
            "Authorization": authHeader
        },
        data : body,
        success: text => onSuccess(text),
        error: err => onError(err)
    });
}

function setOutput(text, div) {
    const pretty = JSON.stringify(JSON.parse(text), null, 2);
    $(div).text(pretty);
}

function errorCallback(err) {
    standardErrorCallback(err, "#errorOutput");
}

function standardErrorCallback(err, errorDiv) {
    $(errorDiv).text("Error: " + err.status + ": " + (isJsonString(err.responseText) ? JSON.parse(err.responseText).message : (err.responseText ? err.responseText : err.statusText)));
}

function alertErrorCallback(err, errorDiv, alert) {
    $(errorDiv).text("Error: " + err.status + ": " + (isJsonString(err.responseText) ? JSON.parse(err.responseText).message : (err.responseText ? err.responseText : err.statusText)));
    $(alert).collapse('show');
}

function isJsonString(str) {
    try {
        JSON.parse(str);
    } catch (e) {
        return false;
    }
    return true;
}

function clearFormsAndShowCollapsed(toShow) {
    const exclude = toShow.join(',');
    $(".form-clear").not(exclude).text("");
    $("form").trigger("reset");
    $('.collapse').not(exclude).collapse('hide');

    setTimeout(function () {
        toShow.forEach((regionId) => {
            $(regionId).collapse('show');
        })
    }, 400);  // the collapse takes 350ms, so wait until all are done before showing them
}


function init() {
    $.ajax({
        type: "GET",
        url: "/api/token/get",
        dataType: "text",
        success: text => {
            const u = JSON.parse(text);

            $("#loginEmail").text(u.contact);
            $(".authed").show();

            if (u.roles.findIndex(e => e === "CLIENTKEY_ISSUER") >= 0) {
                $(".ro-cki").show();
            }
            if (u.roles.findIndex(e => e === "OPERATOR_MANAGER") >= 0) {
                $(".ro-opm").show();
            }
            if (u.roles.findIndex(e => e === "ADMINISTRATOR") >= 0) {
                $(".ro-adm").show();
            }
            if (u.roles.findIndex(e => e === "SECRET_MANAGER") >= 0) {
                $(".ro-sem").show();
            }
            if (u.roles.length === 0) {
                $(".ro-nil").show();
            }

            window.__uid2_admin_user = u
            window.__uid2_admin_token = window.__uid2_admin_user.key;
        },
        error: err => {
            // alert("Error: " + err.status + ": " + JSON.parse(err).message);
            $(".notauthed").show();
        }
    });

    $(document).ready(() => {
        const header = $("h1").first();

        if (window.location.origin.includes("prod")) {
            header.text(header.text().replace("Env", "Prod"));
        } else if (window.location.origin.includes("integ")) {
            header.text(header.text().replace("Env", "Integ"));
        } else if (window.location.origin.includes("localhost") || (window.location.origin.includes("127.0.0.1"))) {
            header.text(header.text().replace("Env", "Local"));
        } else {
            header.text(header.text().replace("Env", ""));
        }

        if (window.location.origin.endsWith(".eu")) {
            header.text(header.text().replace("UID2", "EUID"));
        }

        $(".group").on("click", (e) => {
            $(".group").removeClass("btn-primary").addClass("btn-secondary");
            $("#" + e.target.id).removeClass("btn-secondary").addClass("btn-primary");
        })

    });
}

init();
