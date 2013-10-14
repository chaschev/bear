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


if(Java == null){
    console.log('Java is not there :-(');
    var Java = function(){

    };
}else{
    alert('Java is there!!!!');
}

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

Java.isReturnedArray = function(obj){
    if(obj == null) return false;
    if(typeof obj !== 'object') return false;

    var hasFirst = obj.hasOwnProperty('0');
    var hasSecond = obj['1'] != null;

    if(obj.length == 1 && hasFirst) return true;
    if(obj.length == 2 && hasFirst && hasSecond) return true;

    return hasFirst && hasSecond && (obj['2'] != null);
};

Java.isJavaObject = function (v)
{
    return Object.prototype.toString.apply(v) === '[object JavaRuntimeObject]';
};

Java.getObjectClass = function(v){
    if(this.isJavaObject(v)){
        return v.getClass().getName().toString();
    }

    throw "not a java object: " +v;
};

Java.instanceOf = function(v, jClass){
    return Java.isJavaObject(v) &&
        (Java.getObjectClass(v) === jClass);
};

Java.returnedArrayToJS = function (javaArr){
    var r = [];

    for(var i = 0;i<javaArr.length;i++){
        var v = javaArr[i];

        if(Object.prototype.toString.apply(v) === '[object JavaRuntimeObject]'){
            if(v.getClass().getName().toString() === 'java.lang.String'){
                r[i] = v.toString();
            }else{
                r[i] = v;
            }

        }else{
            r[i] = v;
        }

//        alert(r[i]);
//        alert(typeof (r[i].toString()));
//        alert(r[i].toString());
    }

    return r;
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

Java.isFX = Java.mode === 'FX';

Java.printStackTrace = function(e){
    Java.log("[EXCEPTION] " + e);
};

Java.log = function (){
    var arr = Array.prototype.slice.call(arguments);

    var i;
    for (i = arr.length - 1; i >= 0; i--) {
        if (arr[i] == null) {
        } else {
            break;
        }
    }

    arr = arr.slice(0, i + 1);

    var hasConsole = true;

    for (var j = 0; j < arr.length; j++) {
        var obj = arr[j];
        if(obj === undefined){
            arr[j] = 'undefined';
        }else
        if(obj === null){
            arr[j] = 'null';
        }else
        if(typeof obj === 'string'){
            arr[j] = "'" + arr[j] + "'";
        }else
        if(typeof obj === 'object'){
            var strValue = Object.prototype.toString.apply(obj);

            if(strValue === '[object Error]'){
                var message = "EXCEPTION: " + obj.toString();

                if(obj.stack != null){
                    message += "\n" + obj.stack;
                }

                var stackEntries = obj.stack.split(/\n/);

                for (var k = 0; k < stackEntries.length; k++) {
                    var s = stackEntries[k];

                    s = s.substring(s.indexOf('js/'));
                    s = s.substring(s.indexOf('app/'));
                    s = s.substring(s.indexOf('html/'));

                    stackEntries[k] = s;
                }

                stackEntries.unshift(obj.toString());

                arr[j] = stackEntries;
            } else
            if(Java.isJavaObject(obj)){
                arr[j] = obj.toString();     //for Java objects
            } else {
                if(!hasConsole){
                    try {
                        arr[j] = JSON.stringify2(obj);
                    } catch (e) {
                        alert("ERRROR " + e + ", obj: " + obj);
                    }
                }
            }
        }
    }

    if (!hasConsole) {
        alert(arr.join(", "));
    } else {
        switch (arr.length){
            case 1:
                console.log(arr[0]);
                break;
            case 2:
                console.log(arr[0], arr[1]);
                break;
            case 3:
                console.log(arr[0], arr[1], arr[2]);
                break;
            case 4:
                console.log(arr[0], arr[1], arr[2], arr[3]);
                break;
            case 5:
                console.log(arr[0], arr[1], arr[2], arr[3], arr[4]);
                break;
            case 6:
                console.log(arr[0], arr[1], arr[2], arr[3], arr[4], arr[5]);
                break;
            case 7:
                console.log(arr[0], arr[1], arr[2], arr[3], arr[4], arr[5], arr[6]);
                break;
            case 8:
                console.log(arr[0], arr[1], arr[2], arr[3], arr[4], arr[5], arr[6], arr[8]);
                break;
            default:
                console.log(arr);
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


