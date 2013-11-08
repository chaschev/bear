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

var app = angular.module('bear', ['ui.bootstrap', 'ui.ace', 'fx.file.editor', 'pretty.time']);

function isDigestRunning(scope) {
    return scope.$$phase || scope.$root.$$phase;
}
function safeApply(scope, fn) {
    (isDigestRunning(scope)) ? fn() : scope.$apply(fn);
}

app.service('fileManager', [function () {
    this.openFileDialog = function (curDir) {
        return window.bear.call('fileManager', 'openFileDialog', curDir);
    };
    this.listDir = function (curDir) {
        return window.bear.jsonCall('fileManager', 'listDir', JSON.stringify({dir: curDir, extensions: ['groovy', 'java', 'bear'], recursive: false}));
    };
    this.readFile = function (dir, name) {
        return window.bear.call('fileManager', 'readFile', dir, name);
    };
    this.readFileByPath = function (path) {
        return window.bear.call('fileManager', 'readFileByPath', path);
    };
    this.writeFile = function (dir, name, content) {
        return window.bear.call('fileManager', 'writeFile', dir, name, content);
    };
    this.writeFileByPath = function (path, content) {
        return window.bear.call('fileManager', 'writeFileByPath', path, content);
    };
    this.getBaseName = function(path){
        var i = path.lastIndexOf('\\');
        if(i == -1) i = path.lastIndexOf('/');
        return path.substr(i + 1);
    }

}]);

app.service('historyManager', ['fileManager', function(fileManager){
    this.entries = [];

    this.loadEntries = function(){
        this.historyFilePath = JSON.parse(window.bear.jsonCall('conf', 'getPropertyAsFile', 'bear-fx.history')).path;
        this.fileManager = fileManager;

        console.log("this.historyFilePath: " , this.historyFilePath);

        var json = fileManager.readFileByPath(this.historyFilePath);
        if(!json || json.length === 0) return;

        var jsonEntries = JSON.parse(json);

        for (var i = 0; i < jsonEntries.length; i++) {
            var e = jsonEntries[i];
            var r = null;

            Java.log('loading entry: ', e);

            switch(e.type){
                case 'file':
                    r = new FileReferenceHistoryEntry(e.file);
                    break;
                case 'script':
                    r = new ScriptHistoryEntry(e.text);
                    break;
                default:
                    throw "Unknown type: " + e.type;
            }

            r.time = e.time;

            this.entries.push(r);
        }
    };

    this.saveEntries = function(){
        console.log("saving entries...");

        if(this.entries.length >= 100){
            this.entries.slice(this.entries.length - 100, this.entries.length);
        }

        var arrayToSave = [];

        for (var i = 0; i < this.entries.length; i++) {
            var e = this.entries[i];
            arrayToSave.push(e.dup());
        }

        fileManager.writeFileByPath(this.historyFilePath, JSON.stringify(arrayToSave));
    };

    this.addFileRef = function(file){
        this.entries.unshift(new FileReferenceHistoryEntry(file));
        this.saveEntries();
    };

    this.addScript = function(script){
        this.entries.unshift(new ScriptHistoryEntry(script));
        this.saveEntries();
    };
}]);

app.controller('HistoryController', ['historyManager', '$scope', function(historyManager, $scope){
    $scope.historyManager = historyManager;

    $scope.init = function(){
        try {
            console.log('HistoryController - init()');
            historyManager.loadEntries();
        } catch (e) {
            Java.log("exception while loading", e);
        }
    };

    $scope.updateRunScript = function(entry){
        console.log('updateRunScript', entry, entry.getText());
        $scope.$parent.$broadcast("setActiveEditorValue", entry.getText());
//        $scope.editor.setValue();
    };
}]);

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

/*
<div class="task" id="x1">
<div class="command" id="xxx">
    <div class="commandText"></div>
    <span>:set stage='two'<br/></span><span>:set groovyShell.sendToHosts=true</span>
</div>
 */

app.directive("consoleMessages", ['$timeout', '$compile', '$ekathuwa', function ($timeout, $compile, $ekathuwa) {
    return {
        template: '<div class="consoleMessages" ng-transclude></div>',
        restrict: 'E',
        replace: true,
        transclude: true,
        scope: {
        },
        link: function ($scope, $el, attrs) {
            var unprocessedTexts= [];
            var unprocessedCommands = [];
            var unprocessedTasks = [];

            function updateLastParent(lastParent, event) {
                Java.log("updateLastParent", lastParent, event);
                var x = lastParent;
                if (event && (!lastParent || lastParent.timestamp < event.timestamp)) {
                    x = event;
                }
                return x;
            }

            function getLast(arr) {
                return arr.length == 0 ? null : arr[arr.length - 1];
            }

            function updateLastParent$(selector, lastParent) {
                var $lastCommand = $el.find(selector);

                var x = lastParent;

                if ($lastCommand.length > 0) {
                    x = updateLastParent(lastParent, {id: $lastCommand.attr('id'), timestamp: parseInt($lastCommand.attr('timestamp'))});
                }

                return x;
            }

            try {
            Java.log("my el:" , $el, "my terminal is: ", $scope.terminal, " and scope is: ", $scope);

            $scope = $scope.$parent;
//            $scope.terminal = $scope.$parent.terminal;

            Java.log('terminal: ', $scope.terminal.host.name, 'messages element: ', $el);
            var $messages = $el;

            function sortByTS($el){
                $el.sort(function(a,b){
                    return parseInt($(a).attr('timestamp')) < parseInt($(b).attr('timestamp'));
                });
            }

            function quicklyInsertText(e){
                var $parent = $('#' + e.parentId);

                if($parent.length === 0) {
                    if(e.level != null){
                        var lastParent = null;

                        lastParent = updateLastParent(lastParent, getLast(unprocessedCommands));
                        lastParent = updateLastParent(lastParent, getLast(unprocessedTasks));

                        lastParent = updateLastParent$('.command:last', lastParent);
                        lastParent = updateLastParent$('.task:last', lastParent);
                        lastParent = updateLastParent$('.session:last', lastParent);

                        if(!lastParent || lastParent.id == null){
                            Java.log('[WARNING] unable to find parent for ', e);
                        }

                        e.parentId = lastParent.id;
                    }

                    return false;
                }

                var text = e.textAdded;

//                text = text
//                    .replace(/\r\n/g,'<br>')
//                    .replace(/\n/g,'<br>')
//                ;

                var $span = angular.element('<span timestamp="' + e.timestamp + '"' +
                    (e.level != null ? ' level=' + e.level + '"' : '') +
                    '>' + text + '</span>');

                $compile($span.contents())($scope);

                $parent.append($span);

                sortByTS($parent);

                return true;
            }


            $scope.$on("message", function(event, e){
                if(e.console !== $scope.terminal.name){
                    return;
                }

                Java.log('received broadcasted message: ', e);


                function quicklyInsertCommand(e){
                    var $parent = $('#' + e.parentId);

                    if($parent.length === 0) return false;

                    $parent.append($(
                        '<div class="command" timestamp="' + e.timestamp +'" id="' + e.id + '">' +
                        '<div class="commandText"><b>$ ' + e.command + '</b></div>' +
                        '</div>'
                    ));

                    sortByTS($parent);

                    return true;
                }

                function quicklyInsertTask(e){
                    var $parent = $('#' + e.parentId);

                    if($parent.length === 0) return false;
                    var date = new Date();
                    var $el = $(
                        '<div class="task" timestamp="' + e.timestamp + '" id="' + e.id + '">' +
                            '<div class="taskName"><i>' + e.task + '</i><span class="pull-right">{{' + date.getTime() +
                            '|date:"MMM dd, yyyy HH:mm:ss Z"}}</span></div>' +
                            '</div>'
                    );

                    $compile($el.contents())($scope);

                    $parent.append($el);

                    sortByTS($parent);

                    return true;
                }

                function quicklyInsertSession(e){
                    $messages.append($('<div class="session" timestamp="' + e.timestamp + '" id="' + e.id + '" phaseId="' + e.phaseId + '"></div>'));

                    return true;
                }

                if(!e.parentId && !e.level && e.subType!='session'){
                    Java.log('[WARNING] parentId is null for ', e);
                }

                switch(e.subType){
                    case 'textAdded':
//                        $scope.addMessage(e.textAdded);
                        if(!quicklyInsertText(e)){
                            unprocessedTexts.push(e);
                        }
                        break;
                    case 'command':
                        if(!quicklyInsertCommand(e)){
                            unprocessedCommands.push(e);
                        }
                        $scope.addCommand(e.command);
                        break;
                    case 'task':
                        if(!quicklyInsertTask(e)){
                            unprocessedTasks.push(e);
                        }
                        $scope.addTask(e.task);
                        break;
                    case 'session':
                        quicklyInsertSession(e);
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

                var obj, i;

                // the idea of optimization: no need to insert into children
                // if there is no update to parent
//                for (i = 0; i < unprocessedTasks.length; i++) {
//                    obj = unprocessedTasks[i];
//                    if(quicklyInsertTask(obj)) {
//
//                    }
//                }

                unprocessedTasks = unprocessedTasks.filter(function(obj){
                    return !quicklyInsertTask(obj);
                });

                unprocessedCommands = unprocessedCommands.filter(function(obj){
                    return !quicklyInsertCommand(obj);
                });

                unprocessedTexts = unprocessedTexts.filter(function(obj){
                    return !quicklyInsertText(obj);
                });

//                for (i = 0; i < unprocessedCommands.length; i++) {
//                    obj = unprocessedCommands[i];
//                    if() {
//
//                    }
//                }
//
//                for (i = 0; i < unprocessedTexts.length; i++) {
//                    obj = unprocessedTexts[i];
//                    quicklyInsertCommand(obj);
//                }
            });

            $scope.addTask = function(task){
                $timeout(function(){
                    $scope.terminal.currentTask = task;
                });

//                $messages.append($('<div class="console-task btn btn-primary">' + task + '</div>'));
                this.messageCount++;
            };

            $scope.addCommand = function(command){
                $timeout(function () {
                    $scope.terminal.currentCommand = command;
                    $scope.terminal.currentCommandStartedAt = new Date();
                });

//                $messages.append($('<div class="console-command text-info">$ ' + command + '</div>'));
                this.messageCount++;
            };

            var dmp = new diff_match_patch();

            $scope.compareSessions = function(id1, id2){
                //from http://stackoverflow.com/questions/11905943/jquery-text-interpretbr-as-new-line
                function convertToText(id) {
                    var c = document.getElementById(id);
                    return c.textContent || c.innerText;
                }

                var text1 = convertToText(id1);
                var text2 = convertToText(id2);

                console.log('text1: ', text1.indexOf('\n') != -1, text1);

                dmp.Diff_Timeout = 100;
                dmp.Diff_EditCost = 4;

                var d = dmp.diff_main(text1, text2);

                dmp.diff_cleanupSemantic(d);

                var ds = dmp.diff_prettyHtml(d);

                $ekathuwa.modal({
                    id: "compareSessionsDialogId",
                    scope: $scope,
                    contentPreSize: "lg",
                    templateHTML: '' +
                        '<div class="modal fade" id="compareSessionsDialogId" >' +
                        '<div class="modal-dialog">' +
                        '<div class="modal-content">' +
                        '<div class="modal-header">' +
                        ' <button aria-hidden="true" data-dismiss="modal" class="close" type="button">x</button>' +
                        ' <h4 id="myModalLabel" class="modal-title">Compare</h4>' +
                        '</div>' +
                        '<div class="consoleMessages">' + ds + '</div>' +
                        '<div class="modal-footer">' +
                        ' <button data-dismiss="modal" class="btn btn-default" type="button" ng-click="">Close</button></div>' +
                        '</div>' +
                        '</div>' +
                        '</div>'
                });
            };

            $scope.$on("allFinished", function(event, e){
                var terminal = $scope.terminal;
                var groups = e.groups;

                console.log('allFinished, groups', groups, terminal.name);

                if(terminal.name !== 'shell') return;

                var addLink;
                var comparisonLink;
                var diffLinkCaption;

                if(groups.length === 1){
                    var hasEntries = groups[0].entriesIds.length > 0; //hosts > 0

                    comparisonLink = hasEntries ? "compareSessions(" +
                        "'" + groups[0].id + "', '" + groups[0].entriesIds[0] + "')" : "";
                    diffLinkCaption = '[no diff]';

                    addLink = hasEntries;

                }else{
                    comparisonLink = "compareSessions(" +
                        "'" + groups[0].id + "', '" + groups[1].id + "')";
                    diffLinkCaption = '<span class = "diffError">[' + groups[1].distance + '% diff]</span>';
                    addLink = true;
                }

                e.textAdded = "took " + durationToString(e.duration, true) + "s " +
                    (addLink ? '<a ng-click="' + comparisonLink + '">' +diffLinkCaption + '</a>' : diffLinkCaption) +
                    '\n';

                quicklyInsertText(e);


            });

            } catch (e) {
                Java.log(e);
            }
        }
    };
}]);


// @host.name
// @host.address
// @currentTask
// @currentTaskResult
// @currentCommand
// @currentCommandStartedAt
var Terminal = function(host){
    this.host = host;
    this.name = host.name;
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


Terminals.prototype.remoteTerminals = function(){
    var r = [];

    for (var i = 0; i < this.terminals.length; i++) {
        var term = this.terminals[i];

        if(term.name == 'shell' || term.name == 'status') continue;

        r.push(term);
    }

    return r;
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

Terminals.prototype.findByName = function(name){
    var i = this.indexByName(name);
    if( i === -1) return null;
    return this.terminals[i];
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

app.controller('BearCtrl', ['fileManager', '$scope', function (fileManager, $scope){
    $scope.lastBuildTime = new Date();

    $scope.terminals = new Terminals();

    $scope.terminals.updateHosts([
        {
            name: 'shell',
            address: 'shell'
        },
        {
            name: 'status',
            address: 'status'
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

            case 'allFinished':
                $scope.$broadcast('allFinished', e);

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

    $scope.fileManager = fileManager;

    $scope.openFileDialog = function (curDir){
        return window.bear.call('conf', 'openFile', curDir);
    };

    $scope.listDirFunction = function (curDir){
        return window.bear.jsonCall('conf', 'listDir', JSON.stringify({dir: curDir, extensions: ['groovy', 'java', 'bear'], recursive: false}));
    };
}]);


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

    $scope.createPom = function(){
        window.bear.call('conf', 'createPom');
    };

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
app.controller('ConsoleTabsChildCtrl', ['$scope', '$q', '$timeout', 'historyManager', function ($scope, $q, $timeout, historyManager) {
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

    var readRunScript = function (runScript) {
        return $scope.fileManager.readFile(
            runScript.dir,
            runScript.filename
        );
    };

    var refreshScriptText = function(runScript) {
        console.log('refreshScriptText - click!');

        var fn = function () {
            console.log('refreshScriptText - before setValue');
            $scope.editor.setValue(readRunScript(runScript), -1);
            console.log('refreshScriptText - after setValue');
            $scope.runScriptModified = null;
        };

        $timeout(fn);
    };


//    $scope.isRunScriptModified = function(){
//        return $scope.editor.getValue() == readRunScript($scope.runScript);
//    };

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

        Java.log('interpret response:', response);

        if($scope.runScriptModified){
            historyManager.addScript(commandText);
        } else{
            console.log($scope.runScript, $scope);
            historyManager.addFileRef($scope.runScript.path);
        }

        $scope.addCommand(commandText);
        editor.setValue('');

    };


    $scope.refreshRunScript = function(){
        refreshScriptText($scope.runScript);
    };

    $scope.$on('setActiveEditorValue', function(event, text){
//        console.log('setActiveEditorValue', $scope.terminal);
        if($scope.terminal.active){
            $scope.editor.setValue(text, -1);
        }
    });

    $scope.aceLoaded = function(editor){
        Java.log("loaded ace editor");

        $scope.editor = editor;

        $scope.$watch('runScript', function(newVal, oldVal){
            Java.log(newVal, newVal, oldVal);
            if(newVal /*&& newVal.path !== oldVal.path*/){
                refreshScriptText(newVal);
            }
        }, true);

        $scope.$watch('runScript.path', function(newVal, oldVal){
            Java.log('runScript.path watch:', newVal, oldVal);

            if(newVal && newVal !== oldVal){
                editor.setValue($scope.fileManager.readFile(
                    $scope.runScript.dir,
                    $scope.runScript.filename
                ));
            }
        });

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
