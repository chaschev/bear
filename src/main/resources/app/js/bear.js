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
        var list = attrs['chosen'];

        var $element = $(element[0]);

        scope.$watch(list, function(){
            $element.trigger('liszt:updated');
            $element.trigger("chosen:updated");
//            console.log('chosen:updated');
        });

        $element.chosen({width: "100%"});


    };

    return {
        restrict:'A',
        link: linker
    }
});



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
    $scope.selectedOptionIndex = 0;

//    $scope.files= [{name:"Settings.java", id:1}, {name:"XX.java", id:2}];
    $scope.currentTab = function(){
        return ($scope.selectedTab == 'script') ?
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
        $scope.selectedTab = tab.substring(0, tab.length - 3);
        $scope.selectedFile = $scope.getSelectedFile();
        $scope.$digest();
    };

    $scope.$watch('selectedTab', function(newVal, oldVal){
        console.log("selectedTab watch: ",newVal, oldVal, $scope.currentTab());
    });

    $scope.$watch('currentTab()', function(newVal, oldVal){
        console.log("currentTab watch: ", newVal, oldVal);
    });

    $scope.$watch('files()', function(newVal, oldVal){
        console.log("files watch: ", newVal);
    });

    $scope.$watch('file', function(newVal, oldVal){
        console.log("file watch: ", newVal);
    });

    $scope.$watch('selectedFile', function(newVal, oldVal){
        console.log("selectedFile watch: ", newVal);
        $scope.currentTab().selectedFile = newVal;
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