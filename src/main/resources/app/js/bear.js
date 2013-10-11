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

var module = angular.module('bear', ['ui.bootstrap']);

module.directive('chosen',function() {
    console.log('chosen!!');

    var linker = function(scope,element,attrs) {
        var selected = attrs['chosen'];

        var $element = $(element[0]);

        scope.$watch(selected, function(){
            Java.log('model ', scope[selected], 'files: ', scope.files(), ' and updating');
            Java.log('selected val:' + $element.html(), $element.val());

            $element.trigger('liszt:updated');
            $element.trigger("chosen:updated");
        });

        $element.chosen({width: "100%"});
    };

    return {
        restrict:'A',
        link: linker
    }
});

function BearCtrl($scope){
    $scope.lastBuildTime = new Date();

    $scope.$watch('lastBuildTime', function(){
//        Java.log("updating fields on new build");

    });

    $scope.buildScripts = function(){
        Java.log("building scripts...");

        window.bear.call('conf', 'build');

        Java.log("done building scripts");

        $scope.lastBuildTime = new Date();
        $scope.$digest();
        $scope.$broadcast('buildFinished');
    }
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

    function mapArray(arr){
        for (var i = 0; i < arr.length; i++) {
            arr[i] = {index:i, name: arr[i]};
        }
    }

    $scope.$on('buildFinished', function(e, args){
        Java.log("buildFinished - updating files");

        $scope.scripts.files = window.bear.call('conf', 'getScriptNames');
        $scope.settings.files = window.bear.call('conf', 'getSettingsNames');

        if($scope.selectedFile == null || $scope.selectedFile === 'Loading'){
            $scope.scripts.selectedFile = window.bear.call('conf', 'getSelectedScript');
            $scope.settings.selectedFile = window.bear.call('conf', 'getSelectedSettings');

            $scope.selectedFile = $scope.scripts.selectedFile;
        }

        Java.log('files:', $scope.scripts.files, "selectedFile:", $scope.selectedFile);

        $scope.selectTab($scope.selectedTab);
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
        Java.log("selectedFile watch: " + newVal + ", updating editor");

        $scope.currentTab().selectedFile = newVal;

        var content = window.bear.call('conf', 'getFileText', newVal);

        var editor = ace.edit($scope.selectedTab + "Text");
        var cursor = editor.selection.getCursor();
        editor.setValue(content, cursor);
    });
}

var TabsDemoCtrl = function ($scope) {
    $scope.tabs = [
        { id: "con1", title:"vm01", active: true, content:'Dynamic content 1 <br/> <div class="input-group-btn"> <button type="button" class="btn btn-default dropdown-toggle" data-toggle="dropdown">Action <span class="caret"></span></button></div>' },
        { id: "con2", title:"vm02", content:"Dynamic content 2" },
        { id: "con3", title:"vm03", content:"Dynamic content 3" }
    ];

    $scope.alertMe = function() {
        setTimeout(function() {
            console.log("You've selected the alert tab!");
        });
    };

    $scope.navType = 'pills';
};

