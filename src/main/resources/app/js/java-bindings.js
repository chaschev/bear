/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

//Object.prototype.__keys__ = function ()
//{
//    return Object.keys(this);
//};
//
//Object.prototype.__keys__.toLowerCase = function(){
//    return "";
//}


var Java = function(){

};

Java.init = function(window){
    Java.log("initializing Java...");
    Java.OpenBean = window.OpenBean;
    Java.Bindings = window.Bindings;
    Java.log("OpenBean: ", Java.OpenBean)
};

Java.initApp = function(){
    Java.log("Java.initApp: set you initialization in Java.initApp = function(){ ... }");
};


Java.Collections = function(){

};

Java.Collections.newArrayList = function(arr){
    var jList = checkExc(Java.Bindings.newArrayList());

    for (var i = 0; i < arr.length; i++) {
        jList.add(arr[i]);
    }

    return jList;
};

Java.Collections.newObjectArray = function(size){
    return checkExc(Java.Bindings.newObjectArray(size));
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

Java.isFX = Java.mode == 'FX';

Java.printStackTrace = function(e){
    Java.log("[EXCEPTION] " + e);
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

checkExc = function (r)
{
    if (r && r.isExceptionWrapper) {
        throw r.stackTrace;
    }

    return r;
};

Java.newInstanceArgsArray = function (className, args)
{
//    Java.log("className&args:", className, args);
    var javaArrayArgs = Java.Collections.toJavaArray(args);

//    Java.log("className&args:", className, javaArrayArgs);

    return checkExc(Java.Bindings.newInstance(className, javaArrayArgs));
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
        //constructor for a new instance of NewClass which returns the unwrapped instance
        this.name = className;
        var args = Array.prototype.slice.call(arguments);

        this.instance = Java.newInstanceArgsArray(className, args);

        return this.instance;
    };

    // a bit more efficient version
    // caching class desc leads to a crash for some reason...
//    var classDesc = checkExc(Java.Bindings.getClassDesc(className));
//
//    var NewClass = function(){
//
//        var args = Array.prototype.slice.call(arguments);
//
//        var javaParams = Java.Collections.toJavaArray(args);
//
//        return checkExc(Java.Bindings.newInstanceFromDesc(classDesc, javaParams));
//    };

    var fields = checkExc(Java.Bindings.getStaticFieldNames(className));
    var fieldValues = checkExc(Java.Bindings.getStaticFieldValues(className));

    var staticMethods = checkExc(Java.Bindings.getStaticMethods(className));

    var i;

    for (i = 0; i < fields.length; i++) {
        NewClass[fields[i]] = fieldValues[i];
    }

    //may be call directly?

    function newClosure(method)
    {
        return function ()
        {
            var args = Array.prototype.slice.call(arguments);

            return checkExc(Java.Bindings.callStatic(className, method, Java.Collections.toJavaArray(args)));
        };
    }

    for (i = 0; i < staticMethods.length; i++) {
        var closure = newClosure(staticMethods[i]);

        closure.methodName = staticMethods[i];

        NewClass[staticMethods[i]] = closure
    }

    return NewClass;
};


