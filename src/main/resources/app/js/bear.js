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

var app = angular.module('bear', ['ui.bootstrap']);

app.directive('chosen',function() {
    return {
        restrict: 'A',
        link: function (scope, element, attrs)
        {
            var selected = attrs['chosen'];

            var $element = $(element[0]);

            scope.$watch(selected, function ()
            {
                $element.trigger('liszt:updated');
                $element.trigger("chosen:updated");
            });

            $element.chosen({width: "100%"});
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
        link: function (scope, $el, attrs) {
            try {
                Java.log("my terminal is: ", scope.terminal, " and scope is: ", scope);
            } catch (e) {
                Java.log(e);
            }

            console.log('this element: ', $el);
            var $messages = $el;

            scope.addMessage = function(text){
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
            };

            scope.addTask  = function(task){
                $messages.append($('<div class="console-task btn btn-primary">' + task + '</div>'));
            };

            scope.addCommand  = function(command){
                $messages.append($('<div class="console-command text-info">$ ' + command + '</div>'));
            };
        }
    };
});


// @host.name
// @host.address
// @currentRootTask
// @currentTask
// @currentCommand
var Terminal = function(host){
    this.host = host;
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

    $scope.$watch('lastBuildTime', function(){
//        Java.log("updating fields on new build");

    });

    $scope.buildScripts = function(){
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
    };

    $scope.updateHosts = function(hosts){
        $scope.terminals.updateHosts(hosts);
    };

    $scope.dispatchMessage = function(e){
        switch(e.type){
            case 'console':
                var $console = $('#ConsoleTabsCtrl').find('.tabbable > .tab-content > .tab-pane > div[name=' + e.console +']');

                if($console.length == 0){
                    Java.log("warning: console " + e.console + " was not found");
                }

                var scope = $console.scope();

                switch(e.subType){
                    case 'textAdded':
                        scope.addMessage(e.textAdded);
                        break;
                    case 'command':
                        scope.addCommand(e.command);
                        break;
                    case 'task':
                        scope.addTask(e.task);
                        break;
                    default:
                        throw "not yet supported subType:" + e.subType;
                }

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

function FileTabsCtrl($scope) {
    Java.log("FileTabsCtrl init");

    $scope.selectedTab = 'script';

    $scope.scripts = {
        files: ["Loading"]
    };

    $scope.settings = {
        files: ["Loading"]
    };

    $scope.runScript = function(){
        try {
//            var scope = angular.element('#FileTabsCtrl').scope();

//            Java.log('scope', scope);
            Java.log('running script', $scope.scripts.selectedFile);

            var hosts = JSON.parse(window.bear.jsonCall('conf', 'run', $scope.scripts.selectedFile, $scope.settings.selectedFile));

            $scope.$parent.updateHosts(hosts.hosts);
//            Java.log("terminals: ", hosts);
        } catch (e) {
            Java.log(e);
        }
    };


    function mapArray(arr){
        for (var i = 0; i < arr.length; i++) {
            arr[i] = {index:i, name: arr[i]};
        }
    }

    $scope.$on('buildFinished', function(e, args){
        Java.log("buildFinished - updating files");

        try {
            $scope.scripts.files = window.bear.call('conf', 'getScriptNames');
            $scope.settings.files = window.bear.call('conf', 'getSettingsNames');

            if ($scope.selectedFile == null || $scope.selectedFile === 'Loading') {
                Java.log('initializing selectedFile');
                $scope.scripts.selectedFile = window.bear.call('conf', 'getSelectedScript');
                $scope.settings.selectedFile = window.bear.call('conf', 'getSelectedSettings');

                $scope.selectedFile = $scope.scripts.selectedFile;
            }

            Java.log('files:', $scope.scripts.files, "selectedFile:", $scope.selectedFile);

            $scope.selectTab($scope.selectedTab);
        } catch (e) {
            Java.log(e);
        }
    });

//    $scope.files= [{name:"Settings.java", id:1}, {name:"XX.java", id:2}];
    $scope.currentTab = function(){
        return ($scope.selectedTab === 'script') ?
            $scope.scripts : $scope.settings;
    };

    $scope.files = function(){
        return $scope.currentTab().files;
    };

    $scope.getSelectedFile = function(){
        var currentTab = $scope.currentTab();

        if(!currentTab.selectedFile){
            currentTab.selectedFile = currentTab.files[0];
        }

        return currentTab.selectedFile;
    };

    $scope.selectedFile = $scope.getSelectedFile();


    $scope.selectTab = function(tab){
        Java.log('selecting tab: ' + tab);
        $scope.selectedTab = tab;
        $scope.selectedFile = $scope.getSelectedFile();
        $scope.$digest();
    };

    $scope.$watch('selectedTab', function(newVal){
        Java.log("selectedTab watch: ", newVal, "scope:", $scope.currentTab());
    });

    $scope.$watch('currentTab()', function(newVal){
        Java.log("currentTab watch: ", newVal);
    });

    $scope.$watch('files()', function(newVal, oldVal){
        Java.log("files watch: ", newVal);
    });

    $scope.$watch('file', function(newVal, oldVal){
        Java.log("file watch: ", newVal);
    });

    $scope.$watch('selectedFile', function(newVal, oldVal){
        Java.log("selectedFile watch: ", newVal, ", updating editor");

        $scope.currentTab().selectedFile = newVal;

        var content;
        if(Java.isFX &&  window.bear.isReady()){
            content = window.bear.call('conf', 'getFileText', newVal);
        }else{
            content = 'Content of file <' + newVal + '>';
        }

        var editor = ace.edit($scope.selectedTab + "Text");
        var cursor = editor.selection.getCursor();
        editor.setValue(content, cursor);
    });
}

var ConsoleTabsCtrl = function ($scope) {

};

var ConsoleTabsChildCtrl = function ($scope) {

};