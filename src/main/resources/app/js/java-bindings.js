/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

Object.prototype.__keys__ = function ()
{
    return Object.keys(this);
};

var PrimaryJavaBindings = function(){

};

PrimaryJavaBindings.OpenBean = window.OpenBean;

var Java = function(){

};

Java.mode = navigator.userAgent.match(/Chrome\/\d\d/) ?
    'Chrome' :
    (navigator.userAgent.match(/Firefox\/\d\d/) ? 'FF' : 'FX');

Java.log = function (arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8)
{
    if (Java.mode == 'FX') {
        var arr = [arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8];
        var i;
        for (i = arr.length - 1; i >= 0; i--) {
            if (arr[i] == null) {
                arr.pop();
            } else {
                break;
            }
        }

        arr = arr.slice(i, arr.length);

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
    args = args[0];

    var r = '[';

    for (var i = 0; i < arguments.length; i++) {
        r += arguments[i];

        if(i!=arguments.length-1){
            r+=", ";
        }
    }

    r+=']';

    return r;
}

Java.getClass = function(className){
    Java.log("getClass: " + className);

    var NewClass = function(args){
        //todo call OpenBean.newInstance
        Java.log("NewClass<init>: " + joinArguments(arguments));
        Java.log("NewClass<init>, ", this);
        Java.log("NewClass<init>, ", {foo: 'bar'});
        Java.log("NewClass<init>: " + arguments.__keys__());
        this.instance = PrimaryJavaBindings.OpenBean.newInstance(className, arguments);
        return this.instance;
    };

    NewClass.prototype.getInstance = function(){
        Java.log("NewClass.getInstance: " + this.instance);
        return this.instance;
    };

    return NewClass;
};


function examples(){
    var Foo = Java.getClass('chaschev.js.ex.Foo');

    var foo1 = new Foo("s");

    console.log(foo1);
}
