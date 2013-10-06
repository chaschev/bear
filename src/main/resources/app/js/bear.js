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

angular.module('bear', ['ui.bootstrap']);

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

var TabsDemoCtrl = function ($scope) {
    $scope.tabs = [
        { title:"vm01", content:'Dynamic content 1 <br/> <div class="input-group-btn"> <button type="button" class="btn btn-default dropdown-toggle" data-toggle="dropdown">Action <span class="caret"></span></button></div>' },
        { title:"vm02", content:"Dynamic content 2" },
        { title:"vm03", content:"Dynamic content 3" }
    ];



    $scope.alertMe = function() {
        setTimeout(function() {
            console.log("You've selected the alert tab!");
        });
    };

    $scope.navType = 'pills';
};