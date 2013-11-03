/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

console.log('fx.file.editor module');


angular.module('fx.file.editor', ['ui.bootstrap', 'ui.ace', 'ngEkathuwa'])

//should be placed inside i.e. MyModalCtrl
.directive('fxEditorButton', ['$ekathuwa', '$timeout', function($ekathuwa, $timeout) {
    return {
        restrict: 'A',
        scope: {
            fileManager: '=',
            selected: '=',
            classes: '@',
            styles: '@'
        },
        template: '' +
            '<div class="btn-group" style="{{styles}}">' +
            ' <button type="button" ng-click="showModal()" class="btn btn-default">{{selected.filename}}</button>' +
            ' <button type="button" class="btn btn-primary dropdown-toggle" data-toggle="dropdown">' +
            '  <span class="caret"></span>' +
            ' </button>' +
            ' <ul class="dropdown-menu" role="menu">' +
            '  <li ng-repeat="name in filenames()"><a ng-click="selected.filename = name">{{name}}</a></li>' +
            ' </ul>' +
            '</div>',

        link: function ($scope, $element) {
            Java.log('fxEditorButton for ', $element);
            if ($scope.classes) {
                $element.find('button:first-of-type').attr('class', $scope.classes);
                $element.find('button:last-of-type').attr('class', $scope.classes + " dropdown-toggle");
            }

            $scope.getCurrentDir = function () {
                return ($scope.selected.dir = $scope.selected.dir || '.');
            };

            $scope.filenames = function () {
                var files = JSON.parse($scope.fileManager.listDir($scope.getCurrentDir()));
                //todo fix this - there are 4 updates per click
//                Java.log('files', files);

                files = files.files;

                var names = [];

                for (var i = 0; i < files.length; i++) {
                    if (!files[i].dir) {
                        names.push(files[i].name);
                    }
                }

                return names;
            };

            $scope.saveFile = function () {
                var dir = $scope.selected.dir;
                var filename = $scope.selected.filename;
                Java.log("saving to " + dir, filename);
                $scope.fileManager.writeFile(
                    dir,
                    filename,
                    $scope.editor.getValue()
                );
                $timeout(function () {
                    $scope.modified = '';
                });

//                if(!$scope.$$phase){
//                    $scope.$digest();
//                }
            };

            $scope.fileDialog = function () {
                try {
                    Java.log('fileDialog: ', $scope.getCurrentDir(), $scope.fileManager);

                    var path = $scope.fileManager.openFileDialog($scope.getCurrentDir());

                    Java.log('path: ', path);

                    if (path == null) return;

                    var lastSep = path.lastIndexOf('/');

                    if (lastSep == -1 || !lastSep) {
                        lastSep = path.lastIndexOf('\\');
                    }

                    $scope.selected.dir = path.substring(0, lastSep);
                    $scope.selected.filename = path.substr(lastSep + 1);
                    $scope.selected.path = $scope.selected.dir + "/" + $scope.selected.filename;
                } catch (e) {
                    Java.log(e);
                }
            };

            $scope.showModal = function () {
                $ekathuwa.modal({
                    id: "ekathuwaTemlHTMLId",
                    scope: $scope,
                    contentPreSize: "lg",
                    templateHTML: '' +
                        '<div class="modal fade" id="ekathuwaTemlHTMLId" >' +
                        '<div class="modal-dialog">' +
                        '<div class="modal-content">' +
                        '<div class="modal-header">' +
                        ' <button aria-hidden="true" data-dismiss="modal" class="close" type="button">x</button>' +
                        ' <h4 id="myModalLabel" class="modal-title">Edit - {{selected.filename}}{{modified}}</h4>' +
                        '</div>' +
                        '<div ui-ace="{onLoad: aceLoaded}" style="height: 550px"></div>' +
                        '<div class="modal-footer">' +
                        ' <button class="btn btn-primary pull-left" ng-click="fileDialog()"><i class="fa fa-folder-open-o"></i></button>' +
                        ' <button data-dismiss="modal" class="btn btn-default" type="button" ng-click="">Close</button><button class="btn btn-primary" type="button" ng-click="saveFile()">Save changes</button></div>' +
                        '</div>' +
                        '</div>' +
                        '</div>'
                });
            };

            $scope.aceLoaded = function (editor) {
                Java.log("loaded ace editor in a modal", editor.getScrollSpeed());

                editor.setScrollSpeed(200);

                $scope.editor = editor;

                var session = editor.getSession();

                session.setMode("ace/mode/java");
                session.setTabSize(2);

                editor.setOptions({
                    enableBasicAutocompletion: true,
                    enableSnippets: true
                });

                editor.on('change', function (e) {
                    $scope.modified = '*';

                    try {
                        $scope.$digest();
                    } catch (e) {
                    }
                });

                editor.commands.addCommand({
                    name: "copyShortcut",
                    bindKey: {win: "Ctrl-C", mac: "Command-C"},
                    exec: function (editor) {
                        window.bear.call('conf', 'copyToClipboard', editor.getCopyText());
                    }
                });

                editor.commands.addCommand({
                    name: "pasteShortcut",
                    bindKey: {win: "Ctrl-V", mac: "Command-V"},
                    exec: function (editor) {
                        var r = window.bear.call('conf', 'pasteFromClipboard');
                        editor.insert(r);
                    }
                });

                editor.commands.addCommand({
                    name: "saveShortcut",
                    bindKey: {win: "Ctrl-S", mac: "Command-S"},
                    exec: function (editor) {
                        $scope.saveFile();
                    }
                });

                $scope.$watch('selected', function (newVal, oldVal) {
                    var text = $scope.fileManager.readFile(
                        newVal.dir, newVal.filename
                    );

                    editor.setValue(text, -1);
                    $scope.modified = '';
                }, true);
            };
        }
    }
}]);
