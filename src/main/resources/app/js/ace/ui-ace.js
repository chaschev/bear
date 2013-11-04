/**
 * Binds a ACE Ediitor widget
 */

//TODO handle Could not load worker ace.js:1
//DOMException {message: "SECURITY_ERR: DOM Exception 18", name: "SECURITY_ERR", code: 18, stack: "Error: An attempt was made to break through the se…cloudfront.net/src-min-noconflict/ace.js:1:76296)", INDEX_SIZE_ERR: 1…}

console.log('ui.ace');

angular.module('ui.ace', [])
  .constant('uiAceConfig', {})
  .directive('uiAce', ['uiAceConfig', '$timeout', function (uiAceConfig, $timeout) {
    if (angular.isUndefined(window.ace)) {
      throw new Error('ui-ace need ace to work... (o rly?)');
    }
    return {
      restrict: 'EA',
      require: '?ngModel',
        link: function (scope, elm, attrs, ngModel) {
            var options, opts, editor, session, onChange;

            options = uiAceConfig.ace || {};
            opts = angular.extend({}, options, scope.$eval(attrs.uiAce));

            editor = window.ace.edit(elm[0]);
            session = editor.getSession();

            // this is hack to difference onChange during typing and onChange during setValue
            var superSetValue = editor.setValue;

            var setValueInProgress = false;

            editor.setValue = function(text, cursor){
                console.log('overloaded setValue');

                setValueInProgress = true;

                var r = superSetValue.call(editor, text, cursor);

                setValueInProgress = false;

                return r;
            };

            // end of hack

            var modified = attrs['modified'];

            onChange = function (callback) {
                console.log('onChange initial call');
                return function (e) {
                    var newValue = session.getValue();
                    console.log('onChange outside $timeout', setValueInProgress, e, e.data);

                    var noModification = setValueInProgress;

                    if (newValue !== scope.$eval(attrs.value)) {
                        $timeout(function () {
                            console.log('onChange inside $timeout', modified, noModification);
                            if(modified != null && !noModification){
//                                scope.$parent[modified] = '*';
//                                scope.$parent.$parent[modified] = '*';
                                scope[modified] = '*';
                            }

                            if (angular.isDefined(ngModel)) {
                                ngModel.$setViewValue(newValue);
                            }

                            /**
                             * Call the user onChange function.
                             */
                            if (angular.isDefined(callback)) {
                                if (angular.isFunction(callback)) {
                                    callback(e, editor);
                                } else {
                                    throw new Error('ui-ace use a function as callback.');
                                }
                            }
                        });
                    }
                };
            };


            // Boolean options
            if (angular.isDefined(opts.showGutter)) {
                editor.renderer.setShowGutter(opts.showGutter);
            }
            if (angular.isDefined(opts.useWrapMode)) {
                session.setUseWrapMode(opts.useWrapMode);
            }

            // onLoad callback
            if (angular.isFunction(opts.onLoad)) {
                opts.onLoad(editor);
            }

            // Basic options
            if (angular.isString(opts.theme)) {
                editor.setTheme("ace/theme/" + opts.theme);
            }
            if (angular.isString(opts.mode)) {
                session.setMode("ace/mode/" + opts.mode);
            }

            attrs.$observe('readonly', function (value) {
                editor.setReadOnly(value === 'true');
            });

            // Value Blind
            if (angular.isDefined(ngModel)) {
                ngModel.$formatters.push(function (value) {
                    if (angular.isUndefined(value) || value === null) {
                        return '';
                    }
                    else if (angular.isObject(value) || angular.isArray(value)) {
                        throw new Error('ui-ace cannot use an object or an array as a model');
                    }
                    return value;
                });

                ngModel.$render = function () {
                    session.setValue(ngModel.$viewValue);
                };
            }

            // EVENTS
            session.on('change', onChange(opts.onChange));

        }
    };
  }]);