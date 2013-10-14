function timeSince(date, useMs) {
    var durationMs = new Date() - date;

    return durationToString(durationMs, useMs);
}

function durationToString(durationMs, useMs){

    var seconds = Math.floor(durationMs / 1000);
    var ms = durationMs - seconds * 1000;

    var interval = Math.floor(seconds / 31536000);

    if (interval > 1) {
        return interval + " y";
    }
    interval = Math.floor(seconds / 2592000);
    if (interval > 1) {
        return interval + " m";
    }
    interval = Math.floor(seconds / 86400);
    if (interval > 1) {
        return interval + " d";
    }
    interval = Math.floor(seconds / 3600);
    if (interval > 1) {
        return interval + " h";
    }
    interval = Math.floor(seconds / 60);

    var r = '';

    if (interval > 1) {
        r = interval + " m ";
    }

    return r + (Math.floor(seconds)  + (useMs ? "." + ms : " s"));
}