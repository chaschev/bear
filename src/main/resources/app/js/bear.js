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

var app = angular.module('bear', ['ui.bootstrap', 'ui.ace', 'fx.file.editor', 'pretty.time', 'ansiToHtml']);

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

    this.loadEntries = function () {
        try {
            this.historyFilePath = JSON.parse(window.bear.jsonCall('conf', 'getPropertyAsFile', 'bear-fx.history')).path;
            this.fileManager = fileManager;

            if (!fileManager) {
                console.warn("ERROR: fileManager not wired!!");
            }

            Java.log("!this.historyFilePath: ", this.historyFilePath);

            var json = fileManager.readFileByPath(this.historyFilePath);

            if (!json || json.length === 0) {
                Java.log("found no history entries (case #1)");
                return;
            }

            var jsonEntries = JSON.parse(json);

            if (jsonEntries.length == 0) {
                Java.log("found no history entries (case #2)");
                return;
            }

            for (var i = 0; i < jsonEntries.length; i++) {
                var e = jsonEntries[i];
                var r = null;

//            Java.log('loading entry: ', e);

                switch (e.type) {
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
        } catch (e) {
            Java.log("loadEntries, exception:", e);
        }
    };

    this.saveEntries = function(){
        Java.log("saving entries...");

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

    this.clear = function () {
        Java.log("clearing entries");
        this.entries = [];
        this.saveEntries();
    };

    this.addNewEntry = function (entry) {
        try {
            var text = entry.getText(this.fileManager);

            var index = -1;

            if(window.logMessages) Java.log('this.addNewEntry', this.fileManager);

            for (var i = 0; i < this.entries.length; i++) {
                var e = this.entries[i];
                if (e.getText(this.fileManager) === text) {
                    index = i;
                    break;
                }
            }

            if (index != -1) {
                entry = this.entries[index];
                entry.time = new Date().getTime();
                this.entries.splice(index, 1);
            }

            if(window.logMessages) Java.log('before unshift', this.entries);
            this.entries.unshift(entry);
            if(window.logMessages) Java.log('after unshift', this.entries);
            this.saveEntries();
        } catch (e) {
            Java.log('[ERROR] addNewEntry, exception!!!', e);
        }
    };

    this.remove = function(e){
        var index = this.entries.indexOf(e);
        if(index != -1){
            this.entries.splice(index, 1);
            this.saveEntries();
        }
    };

    this.addFileRef = function(file){
        Java.log("adding file:" + file);
        var entry = new FileReferenceHistoryEntry(file);

        this.addNewEntry(entry);
    };

    this.addScript = function(script){
        this.addNewEntry(new ScriptHistoryEntry(script));
    };
}]);

app.controller('HistoryController', ['historyManager', '$scope', function(historyManager, $scope){
    $scope.historyManager = historyManager;

    $scope.init = function(){
        try {
            Java.log('HistoryController - init()');
            historyManager.loadEntries();
        } catch (e) {
            Java.log("exception while loading", e);
        }
    };

    $scope.clear = function(){
        historyManager.clear();
    };

    $scope.updateRunScript = function(entry){
//        Java.log('updateRunScript', entry);
        var text = entry.getText(historyManager.fileManager);
        Java.log('updateRunScript', text, entry);
        $scope.$parent.$broadcast("setActiveEditorValue", text);
    };
}]);

app.directive('chosen',function() {
    return {
        restrict: 'A',
        link: function (scope, element, attrs) {
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

function sortByTS($el){
    $el.sort(function(a, b){
        return parseInt($(a).attr('timestamp')) < parseInt($(b).attr('timestamp'));
    });
}

app.directive("consoleMessages", ['$timeout', '$compile', '$ekathuwa', 'ansi2html', function ($timeout, $compile, $ekathuwa, ansi2html) {
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
//                Java.log("updateLastParent", lastParent, event);
                var x = lastParent;
                if (event && (!lastParent || lastParent.timestamp < event.timestamp)) {
                    x = event;
                }
                return x;
            }

            function getLast(arr) {
                return arr.length == 0 ? null : arr[arr.length - 1];
            }

            var updateLastParent$ = function (selector, lastParent) {
                var $lastCommand = $el.find(selector);

                var x = lastParent;

                if ($lastCommand.length > 0) {
//                    Java.log('updateLastParent$', $lastCommand);
                    x = updateLastParent(lastParent, {id: $lastCommand.attr('id'), timestamp: parseInt($lastCommand.attr('timestamp'))});
                } else{
//                    Java.log('no matches for', selector);
                }

                return x;
            };

            function scrollToBottom() {
                if(!$scope.autoScrollEnabled) return;

                var scroller = $el.parent();

                scroller.animate({ scrollTop: (scroller.prop("scrollHeight")) }, 30);
            }

            try {
                Java.log("my el:", $el, "my terminal is: ", $scope.terminal, " and scope is: ", $scope);

                $scope = $scope.$parent;
//            $scope.terminal = $scope.$parent.terminal;

                Java.log('terminal: ', $scope.terminal.host.name, 'messages element: ', $el);
                var $messages = $el;

                function quicklyInsertText(e) {
                    var $parent = $('#' + e.parentId);

                    if ($parent.length === 0) {
                        if(window.logMessages) Java.log('no parent');
                        if (e.level != null) {
                            if(window.logMessages) Java.log('trying to find the parent...');

                            var lastParent = null;

                            lastParent = updateLastParent(lastParent, getLast(unprocessedCommands));
                            lastParent = updateLastParent(lastParent, getLast(unprocessedTasks));

                            lastParent = updateLastParent$('.command:last', lastParent);
                            lastParent = updateLastParent$('.task:last', lastParent);
                            lastParent = updateLastParent$('.session:last', lastParent);

                            if (!lastParent || lastParent.id == null) {
                                Java.log('[WARNING] unable to find parent for ', e);
                            } else {
                                e.parentId = lastParent.id;
                                if(window.logMessages) Java.log('parent: ' + lastParent.id, $('#' + lastParent.id));
                            }

                        }

                        return false;
                    }

                    var text;

                    if (e.textAdded) {
                        text = e.textAdded;

                        text = ansi2html.toHtml(angular.element('<div/>').text(text).html());
                    } else {
                        text = e.htmlAdded;
                    }

                    var $span = angular.element('<span timestamp="' + e.timestamp + '"' +
                        (e.level != null ? ' level=' + e.level + '"' : '') +
                        '>' + text + '</span>');

                    $compile($span.contents())($scope);

                    $parent.append($span);

                    sortByTS($parent);

                    scrollToBottom();

                    return true;
                }

                function quicklyInsertCommand(e) {
                    var $parent = $('#' + e.parentId);

                    if ($parent.length === 0) return false;

                    $parent.append($(
                        '<div class="command" timestamp="' + e.timestamp + '" id="' + e.id + '">' +
                            '<div class="commandText"><b>$ ' + e.command + '</b></div>' +
                            '</div>'
                    ));

                    sortByTS($parent);

                    scrollToBottom();

                    return true;
                }

                function quicklyInsertTask(e) {
                    var $parent = $('#' + e.parentId);

                    if ($parent.length === 0) return false;
                    var date = new Date();
                    var $el = $('<div class="task" timestamp="' + e.timestamp + '" id="' + e.id + '" phaseId="' + e.phaseId + '">' +
                        '<div class="taskName"><i>' + e.task + '</i><span class="pull-right">{{' + date.getTime() +
                        '| date:"MMM dd, yyyy HH:mm:ss Z"}}</span></div>' +
                        '</div>'
                    );

                    $compile($el.contents())($scope);

                    $parent.append($el);

                    sortByTS($parent);

                    scrollToBottom();

                    return true;
                }

                function quicklyInsertSession(e) {
                    $messages.append($('<div class="session" timestamp="' + e.timestamp + '" id="' + e.id + '"></div>'));

                    scrollToBottom();

                    return true;
                }


                $scope.$on("message", function (event, e) {
                    if (e.console !== $scope.terminal.name) {
                        return;
                    }

                    if(window.logMessages){
                        Java.log($scope.terminal.name, 'received broadcasted message: ', e);
                    }

                    if (!e.parentId && !e.level && e.subType != 'session') {
                        Java.log('[WARNING] parentId is null for ', e);
                    }

                    switch (e.subType) {
                        case 'textAdded':
//                        $scope.addMessage(e.textAdded);
                            if (!quicklyInsertText(e)) {
                                unprocessedTexts.push(e);
                            }
                            break;

                        case 'command':
                            if (!quicklyInsertCommand(e)) {
                                unprocessedCommands.push(e);
                            }
                            $scope.addCommand(e.command);
                            break;

                        case 'task':
                            if (!quicklyInsertTask(e)) {
                                unprocessedTasks.push(e);
                            }
                            $scope.addTask(e.task);
                            break;

                        case 'newPhase':
                            quicklyInsertSession(e);
                            break;

                        case 'cellFinished':
                            $scope.$apply(function () {
                                $scope.terminal.cellCompleted(e);
                            });
                            break;
                        case 'partyFinished':
                            $scope.$apply(function () {
                                $scope.terminal.partyCompleted(e);
                            });
                            break;
                        default:
                            throw "not yet supported subType:" + e.subType;
                    }

                    unprocessedTasks = unprocessedTasks.filter(function (obj) {
                        return !quicklyInsertTask(obj);
                    });

                    unprocessedCommands = unprocessedCommands.filter(function (obj) {
                        return !quicklyInsertCommand(obj);
                    });

                    unprocessedTexts = unprocessedTexts.filter(function (obj) {
                        return !quicklyInsertText(obj);
                    });
                });

                $scope.addTask = function (task) {
                    $timeout(function () {
                        $scope.terminal.currentTask = task;
                    });

                    this.messageCount++;
                };

                $scope.addCommand = function (command) {
                    $timeout(function () {
                        $scope.terminal.currentCommand = command;
                        $scope.terminal.currentCommandStartedAt = new Date();
                    });

                    this.messageCount++;
                };

                var dmp = new diff_match_patch();

                $scope.compareSessions = function (id1, id2) {
                    //from http://stackoverflow.com/questions/11905943/jquery-text-interpretbr-as-new-line
                    function convertToText(id) {
                        var c = document.getElementById(id);
                        return c.textContent || c.innerText;
                    }

                    var text1 = convertToText(id1);
                    var text2 = convertToText(id2);

                    Java.log('text1: ', text1.indexOf('\n') != -1, text1);

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

                $scope.$on("phaseFinished", function (event, e) {
                    var terminal = $scope.terminal;
                    var groups = e.groups;

                    Java.log('phaseFinished, groups', e, terminal.name);

                    if (terminal.name !== 'shell') return;

                    var addLink;
                    var comparisonLink;
                    var diffLinkCaption;

                    if (groups.length === 1) {
                        var hasEntries = groups[0].entriesIds.length > 0; //hosts > 0

                        comparisonLink = hasEntries ? "compareSessions(" +
                            "'" + groups[0].id + "', '" + groups[0].entriesIds[0] + "')" : "";
                        diffLinkCaption = '[no diff]';

                        addLink = hasEntries;

                    } else if (groups.length > 1) {
                        comparisonLink = "compareSessions(" +
                            "'" + groups[0].id + "', '" + groups[1].id + "')";
                        diffLinkCaption = '<span class = "diffError">[' + groups[1].distance + '% diff]</span>';
                        addLink = true;
                    }

                    if (groups.length > 0) {
                        e.htmlAdded = "took " + durationToString(e.duration, true) + "s " +
                            (addLink ? '<a ng-click="' + comparisonLink + '">' + diffLinkCaption + '</a>' : diffLinkCaption) +
                            '(' + e.phaseName + ')' +
                            '\n';


                        quicklyInsertText(e);
                    }
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
    this.state = 'not.running'; // not.running -> pending -> [complete, cancelling -> not-running]
};

Terminal.prototype.getCssStatus = function(){
    var result;

    if(this.isPending()){
        result = "";
    }else{
        if(!this.currentTaskResult) {
            result = 'danger';
        }else{
            result = (this.currentTaskResult.result === 'OK') ? "success" : "danger";
        }
    }

    return  result;
};

Terminal.prototype.isPending = function ()
{
    return this.state == 'pending';
};

Terminal.prototype.cancel = function ()
{
    this.state = 'cancelling';
    this.currentTaskResult = 'CANCELLED';
    window.bear.call('conf', 'cancelThread', this.name);
};

Terminal.prototype.cellCompleted = function (event)
{
//    this.currentTaskResult = event.result;
    this.lastTaskDuration = event.duration;
};

Terminal.prototype.partyCompleted = function (event)
{
    Java.log('partyCompleted, event: ', event);
    this.state = 'complete';
    this.currentTaskResult = event.result;
    this.lastTaskDuration = event.duration;
};

Terminal.prototype.phaseCompleted = function (event)
{
//    this.lastTaskDuration = event.duration;
    this.currentTaskResult = event.result;
};


Terminal.prototype.getStringStatus = function(){
    switch (this.state){
        case 'not.running': return "Not Running";
        case 'pending': return "Pending... " + this.currentTaskTime();
        case 'cancelling': return "Cancelling... " + this.currentTaskTime();
        case 'complete': return "Complete in " + this.currentTaskTime();
    }

    return "illegal state " + this.state;
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
    this.state = 'pending';
};

// @stats: see Stats class in Java
var Terminals = function(){
    this.terminals = [];
    this.state = 'not.running';         // not.running -> pending -> [complete, cancelling -> not-running]

    this.stats = {
        partiesArrived: 0,
        partiesOk: 0,
        partiesPending: 0,
        partiesFailed: 0,
        partiesCount: 0,
        rootTask: "not run"
    };
};

Terminals.prototype.isPending = function (){
    return this.state === 'pending';
};

Terminals.prototype.cancelAll = function ()
{
    this.eachRemoteShell(function(term){
        if(term.state == 'pending'){
            term.cancel();
        }
    });
};


Terminals.prototype.eachRemoteShell = function (fn) {
    for (var i = 0; i < this.terminals.length; i++) {
        var term = this.terminals[i];

        if (term.name == 'shell' || term.name == 'status') continue;

        fn(term);
    }
};
Terminals.prototype.remoteTerminals = function(){
    var r = [];

    this.eachRemoteShell(function(term){r.push(term);});

    return r;
};

Terminals.prototype.onScriptStart = function(hosts){
    this.updateHosts(hosts);
    this.state = 'pending';

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

    if(stats.partiesArrived === stats.partiesCount){
        this.state = 'not.running';
    }
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

var ProjectInfos = function(){
    this.infos = [];

    this.selectedProject = 'not set';
    this.selectedMethod = 'not set';
};

ProjectInfos.prototype.getSelectedProject = function(){
    for (var i = 0; i < this.infos.length; i++) {
        var project = this.infos[i];
        if(project.name === this.selectedProject){
            return project;
        }
    }

    return null;
};

var ProjectInfo = function(){
    this.name = '';
    this.shortName = 'not set';
    this.methods = [];
    this.path = 'not set';
    this.defaultMethod = 'not set';
};

app.controller('BearCtrl', ['fileManager', '$scope', function (fileManager, $scope){
    $scope.lastBuildTime = new Date();

    $scope.terminals = new Terminals();

    $scope.terminals.updateHosts([{
            name: 'shell',
            address: 'shell'
        }, {
            name: 'status',
            address: 'status'
        }]);

    $scope.initProjects = function(){
        $scope.projectInfos = $.extend(new ProjectInfos(), JSON.parse(window.bear.jsonCall('conf', 'getProjectInfos')));

        Java.log('initProjects, projectInfos: ', $scope.projectInfos);

        $scope.project = $scope.projectInfos.getSelectedProject();
        $scope.method = $scope.projectInfos.selectedMethod;

        Java.log('initProjects, prj&method: ', $scope.project, $scope.method);

        $scope.projectInfos.selectedProject = null;
        $scope.projectInfos.selectedMethod = null;
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

    function mapArray(arr){
        for (var i = 0; i < arr.length; i++) {
            arr[i] = {index:i, name: arr[i]};
        }
    }

    $scope.$on('buildFinished', function(e, args){
        Java.log("buildFinished - $on - updating files");
    });

    $scope.updateOnBuild = function(){
        try {
            Java.log("updateOnBuild - updating files");
        }catch(e){
            Java.log("exception: ", e);
        }
    };

    $scope.dispatchMessage = function(e){
        switch(e.type){
            case 'rmi':
                if(e.subType === 'rootCtrl'){
                    var field = e.bean;
                    var bean;
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
            case 'notice':
                $scope.showNotice(e);

                break;
            case 'phaseFinished':
                $scope.$broadcast('phaseFinished', e);

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

    $scope.showNotice = function(e){
        var className;
        //alert - success - error - warning - information - confirmation
        switch(e.level){
            case 2:
                className = 'error';
                break;
            case 3:
                className = 'warning';
                break;
            case 4:
                className = 'information';
                break;
            case 1:
                className = 'success';
                break;
            default:
                className = 'alert';
                break;
        }

        var n = noty({
            layout: e.position || 'topRight',
            text: "<b>" + (e.title == null ? '' : e.title + ": ") + "</b>" + e.message,
            timeout: e.timeout || 15000,
            type: className,
            maxVisible: 9
        });
    };

    $scope.openFileDialog = function (curDir){
        return window.bear.call('conf', 'openFile', curDir);
    };

    $scope.listDirFunction = function (curDir){
        return window.bear.jsonCall('conf', 'listDir', JSON.stringify({dir: curDir, extensions: ['groovy', 'java', 'bear'], recursive: false}));
    };
}]);


app.controller('FileTabsCtrl', ['$scope', '$q', '$timeout', function($scope, $q, $timeout) {
    Java.log("FileTabsCtrl init");

    $scope.$watch('project', function(newVal, oldVal){
        Java.log("project changed to ", newVal);
        Java.log("setting method to ", newVal.defaultMethod);
        $scope.method = newVal.defaultMethod;
    });

    $scope.$watch('method', function(newVal, oldVal){
        Java.log("method changed to ", newVal);
    });

    $scope.runProject = function(){
        var runObject = {
            projectPath: $scope.project.path,
            projectMethodName: $scope.method,
            shell: 'shell'
        };

        Java.log("about to send: ", runObject);

        var response = JSON.parse(window.bear.jsonCall('conf', 'run',
            JSON.stringify(runObject)));
    };
}]);

var ConsoleTabsCtrl = function ($scope) {

};

//app.controller('FileTabsCtrl', ['$scope', function($scope) {
app.controller('ConsoleTabsChildCtrl', ['$scope', '$q', '$timeout', 'historyManager', function ($scope, $q, $timeout, historyManager) {
    var updateShell = function (shellPlugin)
    {
        Java.log('updating shell to', shellPlugin);

        var commandText = '';

        switch (shellPlugin) {
            case 'sh':
                commandText = ':use shell ssh\n';
                break;
            case 'groovy':
                commandText = ':use shell groovy\n';
                break;
        }

        $scope.sendCommand(commandText);
    };

    $scope.$watch('shellPlugin', function(newVal, oldVal){
        if (newVal !== oldVal) {
            Java.log("'shellPlugin' changed to:" + newVal);
            updateShell(newVal);
        }
    });

//    var readRunScript = function (runScript) {
//        return $scope.fileManager.readFile(
//            runScript.dir,
//            runScript.filename
//        );
//    };

    //todo change this to the project file
    var refreshScriptText = function(runScript) {
        Java.log('refreshScriptText - click!');

        var fn = function () {
            Java.log('refreshScriptText - before setValue');
            $scope.editor.setValue(readRunScript(runScript), -1);
            Java.log('refreshScriptText - after setValue');
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

        Java.log('sendCommand \'' + commandText +"', terminal: ", $scope.terminal.name);

        var response = JSON.parse(window.bear.jsonCall('conf', 'interpret', commandText,
            JSON.stringify({
                projectPath: $scope.project.path,
                projectMethodName: $scope.projectMethodName,
                shell: $scope.terminal.name,
                plugin: $scope.shellPlugin
            })));

        Java.log('interpret response: ', response);

        historyManager.addScript(commandText);

        $scope.addCommand(commandText);
        editor.setValue('');
    };


    $scope.$on('setActiveEditorValue', function(event, text){
//        Java.log('setActiveEditorValue', $scope.terminal);
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

            for(var i = 0;i < prevRow; i++){
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
