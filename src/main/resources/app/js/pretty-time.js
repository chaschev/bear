prettyTime = angular.module('pretty.time', []);

function timeSince(date, useMs) {
    var durationMs = new Date() - date;

    return durationToString(durationMs, useMs);
}

function durationToString(durationMs, useMs){
    var seconds = Math.floor(durationMs / 1000);
    var ms = durationMs - seconds * 1000;

    var interval = Math.floor(seconds / 31536000);

    if (interval > 1) {
        return interval + "y";
    }

    interval = Math.floor(seconds / 2592000);

    if (interval > 1) {
        return interval + "m";
    }
    interval = Math.floor(seconds / 86400);

    if (interval > 1) {
        return interval + "d";
    }

    interval = Math.floor(seconds / 3600);

    if (interval > 1) {
        return interval + "h";
    }

    var minutes = Math.floor(seconds / 60);

    var r = '';

    if(minutes >= 3){
        return minutes + "m";
    }
    if (minutes > 1) {
        r = minutes + "m ";
    }

    return r + (Math.floor(seconds - minutes * 60)  + (useMs ? "." + ms : "s"));
}

prettyTime.filter('prettyDuration', function(){
        return function(input, useMs){
            try {
//                console.log('prettyDuration', input, useMs);
                var type = typeof input;

                switch (type) {
                    case 'string':
                        throw "todo: implement date parsing";
                    case 'number':
                        return durationToString(new Date().getTime() - input, useMs);
                }

                throw "not supported: " + type;
            } catch (e) {
                console.log(e);
            }
        };
    }
);

