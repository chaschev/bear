/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

Object.prototype.__keys__ = function ()
{
    return Object.keys(this);
};


var Java = function(){

};

Java.init = function(window){
    Java.log("initializing Java...")
    Java.OpenBean = window.OpenBean;
    Java.Bindings = window.Bindings;
    Java.log("OpenBean: ", Java.OpenBean)
};

Java.Collections = function(){

};

Java.Collections.newArrayList = function(arr){
    var jList = Java.Bindings.newArrayList();

    for (var i = 0; i < arr.length; i++) {
        jList.add(arr[i]);
    }

    return jList;
};

Java.Collections.newObjectArray = function(size){
//    var jList = this.newArrayList(arr);

    return Java.Bindings.newObjectArray(size);
};

Java.Collections.toJavaArray = function(arr){
    var r = this.newObjectArray(arr.length);

    for (var i = 0; i < arr.length; i++) {
        r[i] = arr[i];
    }

    return  r;
};

Java.Collections.newArray = function(arr){
    var jList = this.newArrayList(arr);

    for (var i = 0; i < arr.length; i++) {
        jList.add(arr[i]);
    }

    return jList;
};

Java.mode = navigator.userAgent.match(/Chrome\/\d\d/) ?
    'Chrome' :
    (navigator.userAgent.match(/Firefox\/\d\d/) ? 'FF' : 'FX');

Java.printStackTrace = function(e){
    Java.log("[EXCEPTION] " + e, printStackTrace(e).join("\n"));
};

Java.log = function (arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8)
{
    if (Java.mode == 'FX') {
        var arr = [arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8];
        var i;
        for (i = arr.length - 1; i >= 0; i--) {
            if (arr[i] == null) {
            } else {
                break;
            }
        }

        arr = arr.slice(0, i + 1);

        for (var j = 0; j < arr.length; j++) {
            var obj = arr[j];
            if(typeof obj == 'string'){
                arr[j] = "'" + arr[j] + "'";
            }
        }

        alert(arr.join(", "));
    } else {
        if (arg8 == null) {
            if (arg7 == null) {
                if (arg6 == null) {
                    if (arg5 == null) {
                        if (arg4 == null) {
                            if (arg3 == null) {
                                if (arg2 == null) {
                                    if (arg1 == null) {
                                        console.log(arg1);
                                    } else {
                                        console.log(arg1);
                                    }
                                } else {
                                    console.log(arg1, arg2);
                                }
                            } else {
                                console.log(arg1, arg2, arg3);
                            }
                        } else {
                            console.log(arg1, arg2, arg3, arg4);
                        }
                    } else {
                        console.log(arg1, arg2, arg3, arg4, arg5);
                    }
                } else {
                    console.log(arg1, arg2, arg3, arg4, arg5, arg6);
                }
            } else {
                console.log(arg1, arg2, arg3, arg4, arg5, arg6, arg7);
            }
        } else {
            console.log(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
        }
    }
};

Java.log('loading java bindings library...');

function joinArguments(args){
    Java.log("args:", args.length, arguments.length, args[0], args[1]);
//    args = args[0];

    var r = '[';

    for (var i = 0; i < args.length; i++) {
        r += args[i];

        if(i!=args.length-1){
            r += "; ";
        }
    }

    r += ']';

    return r;
}

Java.newInstanceArgsArray = function (className, args)
{
//    Java.log("className&args:", className, args);
    var javaArrayArgs = Java.Collections.toJavaArray(args);

//    Java.log("className&args:", className, javaArrayArgs);

    return  Java.Bindings.newInstance(className, javaArrayArgs);
};

Java.newInstance = function(){
    var args = Array.prototype.slice.call(arguments);

    var className = args[0];

    args = args.slice(1);

    return this.newInstanceArgsArray(className, args);
};

Java.getClass = function(className){
    Java.log("getClass: " + className);

    var NewClass = function(){
        //todo call OpenBean.newInstance
        var args = Array.prototype.slice.call(arguments);

        Java.log("NewClass<init>: ", className, args);
        this.instance = Java.newInstanceArgsArray(className, args);
        return this.instance;
    };

    NewClass.prototype.getInstance = function(){
        Java.log("NewClass.getInstance: " + this.instance);
        return this.instance;
    };

    return NewClass;
};


