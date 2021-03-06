(function () {
    "use strict";

    var BASE_TEMPLATE_URL = "/admin/partials";
    var BASE_STATIC_URL = "/resources/angular-templates/admin/partials";
    var PAYMENT_PROXY_DESCRIPTIONS = {
        "STRIPE": "Credit card payments",
        "ON_SITE": "On site (cash) payment"
    };
    var admin = angular.module('adminApplication', ['ui.bootstrap', 'ui.router', 'adminDirectives', 'adminServices', 'utilFilters', 'ngMessages']);

    admin.config(function($stateProvider, $urlRouterProvider) {
        $urlRouterProvider.otherwise("/");
        $stateProvider
            .state('index', {
                url: "/",
                templateUrl: BASE_TEMPLATE_URL + "/index.html"
            })
            .state('index.new-organization', {
                url: "new-organization",
                views: {
                    "newOrganization": {
                        templateUrl: BASE_STATIC_URL + "/main/edit-organization.html",
                        controller: 'CreateOrganizationController'
                    }
                }
            })
            .state('index.new-user', {
                url: "new-user",
                views: {
                    "newUser": {
                        templateUrl: BASE_STATIC_URL + "/main/edit-user.html",
                        controller: 'CreateUserController'
                    }
                }
            })
            .state('events', {
                abstract: true,
                url: '/events',
                templateUrl: BASE_STATIC_URL + "/event/index.html"
            })
            .state('events.new', {
                url: '/new',
                templateUrl: BASE_STATIC_URL + "/event/edit-event.html",
                controller: 'CreateEventController'
            })
            .state('events.detail', {
                url: '/:eventName',
                templateUrl: BASE_STATIC_URL + '/event/detail.html',
                controller: 'EventDetailController'
            })
            .state('events.edit', {
                url: '/:eventName/edit',
                templateUrl: BASE_STATIC_URL + '/event/edit-event.html',
                controller: 'EditEventController'
            })
            .state('configuration', {
                url: '/configuration',
                templateUrl: BASE_STATIC_URL + '/configuration/index.html',
                controller: 'ConfigurationController'
            })
    });

    var validationResultHandler = function(form, deferred) {
        return function(validationResult) {
            if(validationResult.errorCount > 0) {
                angular.forEach(validationResult.validationErrors, function(error) {
                    form.$setError(error.fieldName, error.message);
                });
                deferred.reject('invalid form');
            }
            deferred.resolve();
        };
    };

    var validationPerformer = function($q, validator, data, form) {
        var deferred = $q.defer();
        validator(data).success(validationResultHandler(form, deferred)).error(function(error) {
            deferred.reject(error);
        });
        return deferred.promise;
    };

    var calcPercentage = function(fraction, total) {
        if(isNaN(fraction) || isNaN(total)){
            return numeral(0.0);
        }
        return numeral(fraction).divide(total).multiply(100);
    };

    var applyPercentage = function(total, percentage) {
        return numeral(percentage).divide(100).multiply(total);
    };

    admin.controller('CreateOrganizationController', function($scope, $state, $rootScope, $q, OrganizationService) {
        $scope.organization = {};
        $scope.save = function(form, organization) {
            if(!form.$valid) {
                return;
            }
            validationPerformer($q, OrganizationService.checkOrganization, organization, form).then(function() {
                OrganizationService.createOrganization(organization).success(function() {
                    $rootScope.$emit('ReloadOrganizations', {});
                    $state.go('index');
                });
            }, angular.noop);
        };
        $scope.cancel = function() {
            $state.go('index');
        };
    });

    admin.controller('CreateUserController', function($scope, $state, $rootScope, $q, OrganizationService, UserService) {
        $scope.user = {};
        $scope.organizations = {};
        OrganizationService.getAllOrganizations().success(function(result) {
            $scope.organizations = result;
        });

        $scope.save = function(form, user) {
            if(!form.$valid) {
                return;
            }
            validationPerformer($q, UserService.checkUser, user, form).then(function() {
                UserService.createUser(user).success(function() {
                    $rootScope.$emit('ReloadUsers', {});
                    $state.go('index');
                });
            }, angular.noop);
        };

        $scope.cancel = function() {
            $state.go('index');
        };

    });

    var createCategory = function(sticky, $scope) {
        var lastCategory = _.last($scope.event.ticketCategories);
        var inceptionDate, notBefore;
        if(angular.isDefined(lastCategory)) {
            inceptionDate = moment(lastCategory.expiration.date).format('YYYY-MM-DD');
            notBefore = inceptionDate;
        } else {
            inceptionDate = moment().format('YYYY-MM-DD');
            notBefore = undefined;
        }

        var category = {
            inception: {
                date: inceptionDate
            },
            tokenGenerationRequested: false,
            expiration: {},
            sticky: sticky,
            notBefore: notBefore
        };
        $scope.event.ticketCategories.push(category);
    };

    var initScopeForEventEditing = function ($scope, OrganizationService, PaymentProxyService, LocationService, $state) {
        $scope.organizations = {};

        OrganizationService.getAllOrganizations().success(function(result) {
            $scope.organizations = result;
        });

        PaymentProxyService.getAllProxies().success(function(result) {
            $scope.allowedPaymentProxies = _.map(result, function(p) {
                return {
                    id: p,
                    description: PAYMENT_PROXY_DESCRIPTIONS[p] || 'Unknown provider ('+p+')  Please check configuration'
                };
            });
        });

        $scope.addCategory = function() {
            createCategory(false, $scope);
        };

        $scope.canAddCategory = function(categories) {
            var remaining = _.foldl(categories, function(difference, category) {
                return difference - category.maxTickets;
            }, $scope.event.availableSeats);

            return remaining > 0 && _.every(categories, function(category) {
                return angular.isDefined(category.name) &&
                    angular.isDefined(category.maxTickets) &&
                    category.maxTickets > 0 &&
                    angular.isDefined(category.expiration.date);
            });
        };

        $scope.cancel = function() {
            $state.go('index');
        };

        $scope.updateLocation = function (location) {
            $scope.loadingMap = true;
            LocationService.geolocate(location).success(function(result) {
                $scope.event.geolocation = result;
                $scope.loadingMap = false;
            }).error(function() {
                $scope.loadingMap = false;
            });
        };
    };

    admin.controller('CreateEventController', function($scope, $state, $rootScope,
                                                       $q, OrganizationService, PaymentProxyService,
                                                       EventService, LocationService) {

        $scope.event = {
            freeOfCharge: false,
            begin: {},
            end: {}
        };
        initScopeForEventEditing($scope, OrganizationService, PaymentProxyService, LocationService, $state);
        $scope.event.ticketCategories = [];
        createCategory(true, $scope);

        $scope.save = function(form, event) {
            validationPerformer($q, EventService.checkEvent, event, form).then(function() {
                EventService.createEvent(event).success(function() {
                    $state.go('index');
                });
            }, angular.noop);
        };

    });

    admin.controller('EditEventController', function($scope, $state, $rootScope, $stateParams,
                                                       $q, OrganizationService, PaymentProxyService,
                                                       EventService, LocationService) {

        EventService.getEventForUpdate($stateParams.eventName).success(function(result) {
            $scope.event = result;
            initScopeForEventEditing($scope, OrganizationService, PaymentProxyService, LocationService, $state);

        });

        $scope.save = function(form, event) {
            validationPerformer($q, EventService.checkEvent, event, form).then(function() {
                EventService.updateEvent(event).success(function() {
                    $state.go('index');
                });
            }, angular.noop);
        };

    });

    admin.controller('EventDetailController', function ($scope,
                                                        $stateParams,
                                                        OrganizationService,
                                                        EventService,
                                                        LocationService,
                                                        $rootScope,
                                                        PaymentProxyService,
                                                        $state,
                                                        $log) {
        var loadData = function() {
            $scope.loading = true;
            EventService.getEvent($stateParams.eventName).success(function(result) {
                $scope.event = result.event;
                $scope.organization = result.organization;
                $scope.validCategories = _.filter(result.event.ticketCategories, function(tc) {
                    return !tc.expired;
                });
                $scope.loading = false;
                $scope.loadingMap = true;
                LocationService.getMapUrl(result.event.latitude, result.event.longitude).success(function(mapUrl) {
                    $scope.event.geolocation = {
                        mapUrl: mapUrl,
                        timeZone: result.event.timeZone
                    };
                    $scope.loadingMap = false;
                });
            });
        };
        loadData();
        initScopeForEventEditing($scope, OrganizationService, PaymentProxyService, LocationService, $state);
        $scope.evaluateCategoryStatusClass = function(index, category) {
            if(category.expired) {
                return 'category-expired';
            }
            return 'category-' + $rootScope.evaluateBarType(index);
        };

        $scope.evaluateClass = function(token) {
            switch(token.status) {
                case 'WAITING':
                    return 'bg-warning fa fa-cog fa-spin';
                case 'FREE':
                    return 'fa fa-qrcode';
                case 'TAKEN':
                    return 'bg-success fa fa-check';
                case 'CANCELLED':
                    return 'bg-default fa fa-eraser';
            }
        };

        $scope.isTokenViewCollapsed = function(category) {
            return !category.isTokenViewExpanded;
        };

        $scope.isTicketViewCollapsed = function(category) {
            return !category.isTicketViewExpanded;
        };

        $scope.toggleTokenViewCollapse = function(category) {
            category.isTokenViewExpanded = !category.isTokenViewExpanded;
        };

        $scope.toggleTicketViewCollapse = function(category) {
            category.isTicketViewExpanded = !category.isTicketViewExpanded;
        };

        $scope.evaluateTicketStatus = function(status) {
            var cls = 'fa ';

            switch(status) {
                case 'PENDING':
                    return cls + 'fa-warning text-warning';
                case 'ACQUIRED':
                    return cls + 'fa-bookmark text-success';
                case 'CHECKED_IN':
                    return cls + 'fa-check-circle text-success';
                case 'CANCELLED':
                    return cls + 'fa-close text-danger';
            }

            return cls + 'fa-cog';
        };

        $scope.isPending = function(token) {
            return token.status === 'WAITING';
        };

        $scope.isReady = function(token) {
            return token.status === 'WAITING';
        };

        $scope.moveOrphans = function(srcCategory, targetCategoryId, eventId) {
            EventService.reallocateOrphans(srcCategory, targetCategoryId, eventId).success(function(result) {
                if(result === 'OK') {
                    loadData();
                }
            });
        };

        $scope.eventHeader = {};

        $scope.toggleEditHeader = function(editEventHeader) {
            $scope.editEventHeader = !editEventHeader;
        };

        $scope.saveEventHeader = function(form, header) {
            EventService.updateEventHeader(header).then(function(result) {
                if(result.data['errorCount'] == 0) {
                    $scope.editEventHeader = false;
                    loadData();
                } else {
                    form.$setValidity(false);
                    _.forEach(result.data.validationErrors, function(error) {
                        var field = form.editEventHeader[error.fieldName];
                        if(angular.isDefined(field)) {
                            field.$setValidity('required', false);
                            field.$setTouched();
                        }
                    });
                }
            }, function(error) {
                $log.error(error.data);
                alert(error.data);
            });
        };
    });

    admin.controller('MessageBarController', function($scope, $rootScope) {
        $rootScope.$on('Message', function(m) {
            $scope.message = m;
        });
    });

    admin.controller('ConfigurationController', function($scope, ConfigurationService) {
        $scope.loading = true;
        ConfigurationService.loadAll().success(function(result) {
            $scope.settings = result;
            $scope.loading = false;
        });
        
        $scope.removeConfigurationKey = function(key) {
        	$scope.loading = true;
            ConfigurationService.remove(key).then(function() {return ConfigurationService.loadAll();}).then(function(result) {
            	console.log(result);
            	$scope.settings = result.data;
                $scope.loading = false;
            });
        };
        
        $scope.configurationChange = function(conf) {
            if(!conf.value) {
                return;
            }
            $scope.loading = true;
            ConfigurationService.update(conf).success(function(result) {
                $scope.settings = result;
                $scope.loading = false;
            });
        };
    });

    admin.run(function($rootScope) {
        var calculateNetPrice = function(event) {
            if(isNaN(event.regularPrice) || isNaN(event.vat)) {
                return numeral(0.0);
            }
            if(!event.vatIncluded) {
                return numeral(event.regularPrice);
            }
            return numeral(event.regularPrice).divide(numeral(1).add(numeral(event.vat).divide(100)));
        };

        $rootScope.calculateTotalPrice = function(event, alreadySaved) {
            if(isNaN(event.regularPrice) || isNaN(event.vat)) {
                return '0.00';
            }
            var vat = numeral(0.0);
            if(alreadySaved || !event.vatIncluded) {
                vat = applyPercentage(event.regularPrice, event.vat);
            }
            return vat.add(event.regularPrice).value();
        };

        $rootScope.evaluateBarType = function(index) {
            var barClasses = ['danger', 'warning', 'info', 'success'];
            if(index < barClasses.length) {
                return barClasses[index];
            }
            return index % 2 == 0 ? 'info' : 'success';
        };

        $rootScope.calcBarValue = function(categorySeats, eventSeats) {
            return calcPercentage(categorySeats, eventSeats).format('0.00');
        };

        $rootScope.calcCategoryPricePercent = function(category, event) {
            if(isNaN(event.regularPrice) || isNaN(category.price)) {
                return '0.00';
            }
            return calcPercentage(category.price, event.regularPrice).format('0.00');
        };

        $rootScope.calcCategoryPrice = function(category, event) {
            if(isNaN(event.vat) || isNaN(category.price)) {
                return '0.00';
            }
            var vat = numeral(0.0);
            if(event.vatIncluded) {
                vat = applyPercentage(category.price, event.vat);
            }
            return numeral(category.price).add(vat).format('0.00');
        };
    });

})();