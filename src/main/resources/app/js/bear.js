/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

var app = angular.module('bear', ['ui.bootstrap', 'ui.ace', 'fx.file.editor']);

function safeApply(scope, fn) {
    (scope.$$phase || scope.$root.$$phase) ? fn() : scope.$apply(fn);
}

app.directive('chosen',function() {
    return {
        restrict: 'A',
        link: function (scope, element, attrs)
        {
            var $element = $(element[0]);

            scope.$watch(attrs['chosen'], function ()
            {
                $element.trigger('liszt:updated');
                $element.trigger("chosen:updated");
            });

//            $element.find('option')[0].attr('selected', true);
//            $element.trigger("chosen:updated");
//            $element.trigger('liszt:updated');

            $element.chosen({width: "100%"});
        }
    }
});

app.directive('switch',function() {
    return {
        restrict: 'A',
        transclude: true,
        replace:true,
        link: function (scope, element, attrs)
        {
            var template =
                '<div class="make-switch switch-small" data-off-label="local" data-on-label="remote" data-on="success">' +
                    '<input type="checkbox">' +
                '</div>';

            element.append(template);

            var $element = $(element[0]).find('.make-switch');

            $element.bootstrapSwitch();

            var modelAttr = attrs['switch'];

            scope.$watch(modelAttr, function (newVal)
            {
                $element.bootstrapSwitch('setState', newVal === true || newVal === 'true');
            });

            $element.on('switch-change', function (e, data)
            {
                Java.log('switch-change to ' + data.value);
                if(!scope.$$phase) {
                    scope.$apply(function(){
                        scope[modelAttr] = data.value;
                    });
                }
            });
        }
    }
});

var Session = function(index){
    this.index = index;
};


app.directive("consoleMessages", function ($compile) {
    return {
        template: '<div class="consoleMessages" ng-transclude></div>',
        restrict: 'E',
        replace: true,
        transclude: true,
        scope: {
        },
        link: function ($scope, $el, attrs) {
            try {
            Java.log("my el:" , $el, "my terminal is: ", $scope.terminal, " and scope is: ", $scope);

            $scope = $scope.$parent;
//            $scope.terminal = $scope.$parent.terminal;

            Java.log('terminal: ', $scope.terminal.host.name,'messages element: ', $el);
            var $messages = $el;

            $scope.$on("message", function(event, e){
                if(e.console !== $scope.terminal.host.name){
                    return;
                }

                Java.log('received broadcasted message: ', e);

                switch(e.subType){
                    case 'textAdded':
                        $scope.addMessage(e.textAdded);
                        break;
                    case 'command':
                        $scope.addCommand(e.command);
                        break;
                    case 'task':
                        $scope.addTask(e.task);
                        break;
                    case 'rootTaskFinished':

                        $scope.$apply(function(){
                            $scope.terminal.lastTaskDuration = e.duration;
                            $scope.terminal.currentTaskResult = e.result;
                        });
                        break;
                    default:
                        throw "not yet supported subType:" + e.subType;
                }
            });

            $scope.addMessage = function(text){
                var $prev = $messages.find(".console-message:last");
                var prevHtml = $prev.html();

//                Java.log($messages, $prev);

                // make sure previous line ends with "\n". If it's not, we append a full line to the last element.

                if(prevHtml && prevHtml[prevHtml.length -1]!='\n' && prevHtml[prevHtml.length]!='\r'){
                    var indexOfEOL = text.indexOf('\n');
                    if(indexOfEOL == -1){
                        $prev.append(text.replace('\n', '<br>'));
                        text = '';
                    }else{
                        $prev.append(text.substring(0, indexOfEOL).replace('\n', '<br>'));
                        text = text.substring(indexOfEOL).replace('\n', '<br>');
                    }
                }

                if(text !== ''){
                    $messages.append($('<div class="console-message">' + text + '</div>'));
                }

                this.messageCount++;
            };

            $scope.addTask  = function(task){
                $scope.$apply(function(){
                    $scope.terminal.currentTask = task;
                });

                $messages.append($('<div class="console-task btn btn-primary">' + task + '</div>'));
                this.messageCount++;
            };

            $scope.addCommand  = function(command){
                //todo ask at SO if this is a good practice
                var updateCommand = function () {
                    $scope.terminal.currentCommand = command;
                    $scope.terminal.currentCommandStartedAt = new Date();
                };

                if(!$scope.$$phase){
                    $scope.$apply(updateCommand);
                } else {
                    updateCommand();
                }

                $messages.append($('<div class="console-command text-info">$ ' + command + '</div>'));
                this.messageCount++;
            };

            } catch (e) {
                Java.log(e);
            }
        }
    };
});


// @host.name
// @host.address
// @currentTask
// @currentTaskResult
// @currentCommand
// @currentCommandStartedAt
var Terminal = function(host){
    this.host = host;
    this.messageCount = 0;
};

Terminal.prototype.getCssStatus = function(){
    var result;

    if(this.isPending()){
        result = "";
    }else{
        result = (this.currentTaskResult.result === 'OK') ? "success" : "danger";
    }

    return  result;
};

Terminal.prototype.isPending = function ()
{
    return this.currentTaskResult == null;
};

Terminal.prototype.getStringStatus = function(){
    return (this.isPending() ? 'Pending...' : this.currentTaskResult.result + ", " ) + ' ' + this.currentTaskTime() + ""
};

Terminal.prototype.currentTaskTime = function(){
    if(!this.currentCommandStartedAt){
        return "0.0";
    }

    var duration = this.lastTaskDuration ? this.lastTaskDuration : new Date() - this.currentCommandStartedAt;

    return durationToString(duration, false);
};

Terminal.prototype.onScriptStart = function(){
    Java.log('resetting', this.host);
    this.currentCommandStartedAt = null;
    this.currentCommand = null;
    this.currentTaskResult = null;
    this.lastTaskDuration = null;
};

// @stats: see Stats class in Java
var Terminals = function(){
    this.terminals = [];

    this.stats = {
        partiesArrived: 0,
        partiesOk: 0,
        partiesPending: 0,
        partiesFailed: 0,
        partiesCount: 0,
        rootTask: "not run"
    };
};

Terminals.prototype.onScriptStart = function(hosts){
    this.updateHosts(hosts);

    for (var i = 0; i < this.terminals.length; i++) {
        var term = this.terminals[i];

        term.onScriptStart();
    }
};

Terminals.prototype.updateHosts = function(hosts){
    Java.log('updating hosts with: ', hosts);

    for (var i = 0; i < hosts.length; i++) {
        var host = hosts[i];

        var idx = this.indexByName(host.name);

        if(idx == -1){
            this.terminals.push(new Terminal(host));
        }
    }

    Java.log('updated hosts: ', this.terminals);
};

Terminals.prototype.updateStats = function(stats){
    Java.log('updating stats with: ', stats);
    this.stats = stats;
};

Terminals.prototype.indexByName = function(name){
    for (var i = 0; i < this.terminals.length; i++) {
        var term = this.terminals[i];

        if(term.host.name == name){
            return i;
        }
    }

    return -1;
};

function BearCtrl($scope){
    $scope.lastBuildTime = new Date();

    $scope.terminals = new Terminals();

    $scope.terminals.updateHosts([{
        name: 'shell',
        address: 'shell'
    }]);

    $scope.initScripts = function(){
        $scope.settingsScript = JSON.parse(window.bear.jsonCall('conf', 'getPropertyAsFile', 'bear-fx.settings'));
        $scope.runScript = JSON.parse(window.bear.jsonCall('conf', 'getPropertyAsFile', 'bear-fx.script'));

        Java.log('initScripts, files: ', $scope.settingsScript, $scope.runScript);
    };

    $scope.$watch('lastBuildTime', function(){
//        Java.log("updating fields on new build");

    });

    $scope.buildScripts = function(callback){
        try {
            Java.log("building scripts...");

            window.bear.call('conf', 'build');

            Java.log("done building scripts");
        } catch (e) {
            Java.log("ERROR", e);
        }

        $scope.lastBuildTime = new Date();
        $scope.$digest();
        $scope.$broadcast('buildFinished');

        if(callback != null){
            callback();
        }
    };

    $scope.updateHosts = function(hosts){
        $scope.terminals.updateHosts(hosts);
    };

    $scope.dispatchMessage = function(e){
        switch(e.type){
            case 'rmi':
                if(e.subType === 'rootCtrl'){
                    var field = e.bean;
                    var bean ;
                    var method = e.method;
                    var params = JSON.parse(e.jsonArrayOfParams);

                    if(field == null){
                        bean = $scope;
                    }else{
                        bean = $scope[field];

                        if(bean == null){
                            Java.log("field does not exist in scope: ", field, ", scope: ", $scope);
                            throw "field does not exist in scope: " + field;
                        }
                    }

                    var m = bean[method];
                    if(!m){
                        Java.log("field does not exist in scope: ", method, ", scope: ", $scope);
                        throw "field does not exist in scope: " + method;
                    }

                    Java.log("invoking " + bean + "." + method + "(" + params + ")");
                    $scope.$apply(function(){
                        m.apply(bean, params);
                    });
                }
                break;
            case 'console':
//                Java.log('broadcasting', e);
                $scope.$broadcast('message', e);

                break;
            case 'status':
                $scope.$apply(function(){
                    $scope.terminals.updateStats(e.stats);
                });
                break;
            default:
                Java.log("not yet supported: ", e);
        }
    };

    $scope.fileManager = {
        openFileDialog: function (curDir){
            return window.bear.call('fileManager', 'openFileDialog', curDir);
        },
        listDir: function (curDir){
           return window.bear.jsonCall('fileManager', 'listDir', JSON.stringify({dir: curDir, extensions: ['groovy', 'java'], recursive: false}));
        },
        readFile: function(dir, name){
            return window.bear.call('fileManager', 'readFile', dir, name);
        },
        writeFile: function(dir, name, content){
            return window.bear.call('fileManager', 'writeFile', dir, name, content);
        }
    };

    $scope.openFileDialog = function (curDir){
        return window.bear.call('conf', 'openFile', curDir);
    };

    $scope.listDirFunction = function (curDir){
        return window.bear.jsonCall('conf', 'listDir', JSON.stringify({dir: curDir, extensions: ['groovy', 'java'], recursive: false}));
    };
}


function DropdownCtrl($scope) {
    $scope.items = [
        "The first choice!",
        "And another choice for you.",
        "but wait! A third!"
    ];

    $scope.init = function(items){
        $scope.items = items;
    }
}

app.controller('FileTabsCtrl', ['$scope', '$q', function($scope, $q) {
    Java.log("FileTabsCtrl init");

    // a small cheat
    $scope = $scope.$parent;

    $scope.selectedTab = 'script';

//    $scope.settingsScript = {
//        dir: '.',
//        filename: 'loading'
//    };

    //todo remove
    $scope.runScript = function(){
        try {
//            var scope = angular.element('#FileTabsCtrl').scope();

//            Java.log('scope', scope);
            Java.log('running script', $scope.scripts.selectedFile);

            Java.log('my scope ', $scope, 'parent scope: ', $scope.$parent);

            var hosts = JSON.parse(window.bear.jsonCall('conf', 'run', $scope.runScript.path, $scope.settingsScript.path));

            $scope.terminals.onScriptStart(hosts.hosts);
        } catch (e) {
            Java.log(e);
        }
    };


    function mapArray(arr){
        for (var i = 0; i < arr.length; i++) {
            arr[i] = {index:i, name: arr[i]};
        }
    }

    $scope.updateOnBuild = function(){
        try {
            Java.log("updateOnBuild - updating files");

            //simplifying things
//            $scope.scripts.files = window.bear.call('conf', 'getScriptNames');
//            $scope.settings.files = window.bear.call('conf', 'getSettingsNames');
//
//            if ($scope.selectedFile == null || $scope.selectedFile === 'Loading') {
//                Java.log('initializing selectedFile');
//
//                $scope.scripts.selectedFile = window.bear.call('conf', 'getSelectedScript');
//                $scope.settings.selectedFile = window.bear.call('conf', 'getSelectedSettings');
//
//                $scope.selectedFile = $scope.scripts.selectedFile;
//            }
//
//            Java.log('files:', $scope.scripts.files, "selectedFile:", $scope.selectedFile);
//
//            $scope.selectTab($scope.selectedTab);
        } catch (e) {
            Java.log(e);
        }
    };

    // triggered by
    // this update is needed and is triggered for each build
    $scope.$on('buildFinished', function(e, args){
        Java.log("buildFinished - $on - updating files");

    });
}]);

var ConsoleTabsCtrl = function ($scope) {

};

//app.controller('FileTabsCtrl', ['$scope', function($scope) {
app.controller('ConsoleTabsChildCtrl', ['$scope', '$q', function ($scope, $q) {
    var updateShell = function (remoteEnv, shellPlugin)
    {
        Java.log('updating shell to', remoteEnv, shellPlugin);
        var commandText = '';

        switch (shellPlugin) {
            case 'sh':
                commandText = ':use shell ' + (remoteEnv ? 'ssh' : 'sh') + '\n';
                break;
            case 'groovy':
                commandText = ':use shell ' + shellPlugin + "\n" +
                    ':set groovyShell.sendToHosts=' + remoteEnv;

                break;
        }

        $scope.sendCommand(commandText);
    };

    $scope.$watch('remoteEnv', function(newVal, oldVal){
        if (newVal !== oldVal) {
            Java.log("'remoteEnv' changed to:" + newVal);

            updateShell(newVal, $scope.shellPlugin);
        }
    });

    $scope.$watch('shellPlugin', function(newVal, oldVal){
        if (newVal !== oldVal) {
            Java.log("'shellPlugin' changed to:" + newVal);
            updateShell($scope.remoteEnv, newVal);
        }
    });

    $scope.sendCommand = function (commandText)
    {
        var editor = $scope.editor;
        commandText = commandText || editor.getValue();

        Java.log('sendCommand \'' + commandText +"', terminal:", $scope.terminal.name);

//        var scriptName = $scope.scripts.selectedFile;
        var settingsName = $scope.settingsScript.path;

        if(!settingsName || settingsName === 'Loading'){
            Java.log('cancelled sending a command, because settings is:', settingsName);
            return;
        }

        var response = JSON.parse(window.bear.jsonCall('conf', 'interpret',
            commandText,
            JSON.stringify({
                script: $scope.runScript.path,
                settingsName: settingsName})));

        $scope.addCommand(commandText);
        editor.setValue('');

        Java.log('interpret response:', response);
    };

    $scope.aceLoaded = function(editor){
        Java.log("loaded ace editor");

        $scope.editor = editor;

        var session = editor.getSession();

        session.setMode("ace/mode/java");
        session.setTabSize(2);

        editor.setOptions({
            enableBasicAutocompletion: true,
            enableSnippets: true
        });

        editor.renderer.setShowGutter(false);

        editor.commands.addCommand({
            name: "showKeyboardShortcuts",
            bindKey: {win: "Ctrl-Alt-h", mac: "Command-Alt-h"},
            exec: function(editor) {
                ace.config.loadModule("ace/ext/keybinding_menu", function(module) {
                    module.init(editor);
                    editor.showKeyboardShortcuts()
                })
            }
        });

        editor.commands.addCommand({
            name: "showKeyboardShortcuts",
            bindKey: {win: "Ctrl-Enter", mac: "Command-Enter"},
            exec: function(editor) {
                $scope.sendCommand();
            }
        });

        editor.commands.addCommand({
            name: "copyShortcut",
            bindKey: {win: "Ctrl-C", mac: "Command-C"},
            exec: function(editor) {
                window.bear.call('conf', 'copyToClipboard', editor.getCopyText());
            }
        });

        editor.commands.addCommand({
            name: "pasteShortcut",
            bindKey: {win: "Ctrl-V", mac: "Command-V"},
            exec: function(editor) {
                var r = window.bear.call('conf', 'pasteFromClipboard');
                editor.insert(r);
            }
        });

        function posToOffset(pos){
            var prevRow = pos.row - 1;
            var r = 0;

            for(var i = 0;i < prevRow;i++){
                r += session.getLine(i).length;
            }

            r += pos.column;

            return r;
        }

        function offsetToPos(offset){
            var prevRow = pos.row - 1;
            var linesCount = session.getLength();
            var r = 0;
            var row = 0;

            for(;row < linesCount;row++){
                var lineLength = session.getLine(row).length;

                if(r + lineLength >= offset){
                    break;
                }

                r += lineLength;
            }

            return {row: row, column: offset - r};
        }

        editor.completers.unshift({
            getCompletions: function(editor, session, pos, prefix, callback) {
                var caretPos = posToOffset(pos);

                var completions = JSON.parse(window.bear.call('conf', 'completeCode', editor.getValue(), caretPos));

                Java.log("got " + completions.length + " completions");

                callback(null, completions);
            }
        });
    };

}]);
