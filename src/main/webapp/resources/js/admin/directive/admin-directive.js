(function () {
    "use strict";
    function parseDate(val) {
        var d;
        if(angular.isDefined(val) && (d = moment(val)).isValid()) {
            return d;
        }
        return moment().startOf('day');
    }

    var directives = angular.module('adminDirectives', ['ui.bootstrap', 'adminServices']);

    directives.directive("organizationsList", function() {
        return {
            scope: true,
            templateUrl: '/resources/angular-templates/admin/partials/main/organizations.html',
            controller: function($scope, $rootScope, OrganizationService) {
                var loadOrganizations = function() {
                    OrganizationService.getAllOrganizations().success(function(result) {
                        $scope.organizations = result;
                    });
                };
                $rootScope.$on('ReloadOrganizations', function(e) {
                    loadOrganizations();
                });
                $scope.organizations = [];
                loadOrganizations();
            },
            link: angular.noop
        };
    });
    directives.directive("usersList", function() {
        return {
            scope: true,
            templateUrl: '/resources/angular-templates/admin/partials/main/users.html',
            controller: function($scope, $rootScope, UserService) {
                $scope.users = [];
                var loadUsers = function() {
                    UserService.getAllUsers().success(function(result) {
                        $scope.users = result;
                    });
                };
                $rootScope.$on('ReloadUsers', function() {
                    loadUsers();
                });
                loadUsers();
            },
            link: angular.noop
        };
    });

    directives.directive('eventsList', function() {
        return {
            scope: true,
            templateUrl: '/resources/angular-templates/admin/partials/main/events.html',
            controller: function($scope, EventService) {
                EventService.getAllEvents().success(function(data) {
                    $scope.events = data;
                });
            },
            link: angular.noop
        };
    });

    directives.directive('dateRange', function() {
        return {
            restrict: 'A',
            scope: {
                minDate: '=',
                maxDate: '=',
                startDate: '=',
                startModelObj: '=startModel',
                endModelObj: '=endModel'
            },
            require: '^ngModel',
            link: function(scope, element, attrs, ctrl) {
                var dateFormat = 'YYYY-MM-DD HH:mm';
                var fillDate = function(modelObject) {
                    if(!angular.isDefined(modelObject.date)) {
                        modelObject.date = {};
                        modelObject.time = {};
                    }
                };
                var startDate, endDate;

                if(angular.isDefined(scope.startModelObj.date)) {
                    startDate = moment(scope.startModelObj.date + 'T' + scope.startModelObj.time);
                    endDate = moment(scope.endModelObj.date + 'T' + scope.endModelObj.time);
                    var result = startDate.format(dateFormat) + ' / ' + endDate.format(dateFormat);
                    ctrl.$setViewValue(result);
                    element.val(result);
                } else {
                    fillDate(scope.startModelObj);
                    fillDate(scope.endModelObj);
                    startDate = scope.startDate;
                    endDate = scope.endDate;
                }

                var minDate = scope.minDate || moment();

                element.daterangepicker({
                    format: dateFormat,
                    separator: ' / ',
                    startDate: startDate,
                    endDate: endDate,
                    minDate: minDate,
                    maxDate: scope.maxDate,
                    timePicker: true,
                    timePicker12Hour: false,
                    timePickerIncrement: 15
                });

                element.on('apply.daterangepicker', function(ev, picker) {
                    scope.$apply(function() {
                        var start = picker.startDate();
                        var end = picker.endDate();
                        scope.startModelObj['date'] = start.format('YYYY-MM-DD');
                        scope.startModelObj['time'] = start.format('HH:mm');
                        scope.endModelObj['date'] = end.format('YYYY-MM-DD');
                        scope.endModelObj['time'] = end.format('HH:mm');
                        ctrl.$setViewValue(element.val());
                    });
                });
            }
        };
    });

    directives.directive('grabFocus', function() {
        return {
            restrict: 'A',
            link: function(scope, element, attrs) {
                element.focus();
            }
        };
    });

    directives.directive('controlButtons', function() {
        return {
            restrict: 'E',
            templateUrl: '/resources/angular-templates/admin/partials/form/controlButtons.html',
            scope: {
                formObj: '=',
                cancelHandler: '='
            },
            link: function(scope, element, attrs) {
                scope.cancel = function() {
                    if(angular.isFunction(scope.cancelHandler)) {
                        scope.cancelHandler();
                    } else if(angular.isFunction(scope.$parent.cancel)) {
                        scope.$parent.cancel();
                    }
                }
            }
        };
    });

    directives.directive('fieldError', function() {
        return {
            restrict: 'E',
            templateUrl: '/resources/angular-templates/admin/partials/form/fieldError.html',
            scope: {
                formObj: '=',
                fieldObj: '=',
                minChar: '=',
                requiredPattern: '='
            },
            link:angular.noop
        };
    });
})();