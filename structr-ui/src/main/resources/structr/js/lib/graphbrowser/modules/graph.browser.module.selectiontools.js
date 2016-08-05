var Graphbrowser = Graphbrowser || {};
Graphbrowser.Modules = Graphbrowser.Modules || {};

var animating = animating || undefined;

(function() {
	'use strict';

	var _s, _callbacks, _currentActiveSelection;
	var _hasDragged = false;
	var _timeout = 0;
	var _activeStateSelections = {};

	Graphbrowser.Modules.SelectionTools = function(sigmaInstance, callbacks){
		var self = this;
		_s = sigmaInstance;
		_callbacks = callbacks;
		_hasDragged = false;
		self.name = 'selectionTools';
		self.isActive = false;
	};

	Graphbrowser.Modules.SelectionTools.prototype.types = ["restoreListener"];

	Graphbrowser.Modules.SelectionTools.prototype.init = function(settings) {
		var self = this;

		_callbacks.api.createSelectionGroup = self.createSelectionGroup.bind(self);
		_callbacks.api.deleteSelectionGroup = self.deleteSelectionGroup.bind(self);
		_callbacks.api.activateSelectionGroup = self.activateSelectionGroup.bind(self);
		_callbacks.api.hideSelectionGroup = self.hideSelectionGroup.bind(self);
		_callbacks.api.fixateSelectionGroup = self.fixateSelectionGroup.bind(self);
		_callbacks.api.clearSelectionGroup = self.clearSelectionGroup.bind(self);
		_callbacks.api.activateSelectionLasso = self.activateSelectionLasso.bind(self);
		_callbacks.api.deactivateSelectionTools = self.deactivateSelectionTools.bind(self);
		_callbacks.api.activateSelectionTools = self.activate.bind(self);
		_callbacks.api.selectionToolsActive = self.isActive;
		_callbacks.api.dropSelection = self.dropSelection.bind(self);
	};

	Graphbrowser.Modules.SelectionTools.prototype.activate = function() {
		var self = this;
		if(self.isActive)
			return;
		self.isActive = true;

		_s.renderers[0].bind('render', function(e) {
			_s.renderers[0].halo({
				nodes: _callbacks.sigmaPlugins.activeState.nodes()
			});
		});

		_callbacks.sigmaPlugins.lasso.bind('selectedNodes', function (event) {
			window.clearTimeout(_timeout);
			_timeout = setTimeout(function() {

				if(_activeStateSelections[_currentActiveSelection].hidden){
					self.hideSelectionGroup(_currentActiveSelection, false);
				}
				if(_activeStateSelections[_currentActiveSelection].fixed){
					self.fixateSelectionGroup(_currentActiveSelection, false);
				}

				if(event.data.length <= 0)
					return;

				_activeStateSelections[_currentActiveSelection].nodes = event.data;

				self.activateSelectionLasso(false);
				_s.refresh({skipIndexation: true});
				self.updateActiveState();
				_timeout = 0;
			}, 10);
		});

		self.updateActiveState();
		_s.refresh({skipIndexation: true})
	};

	Graphbrowser.Modules.SelectionTools.prototype.createSelectionGroup = function() {
		var newId = (parseInt(Math.random() * (5000 - 1))).toString();
		_activeStateSelections[newId] = {id: newId, nodes: [], hidden: false, fixed: false};
		_currentActiveSelection = newId;
		return newId;
	};

	Graphbrowser.Modules.SelectionTools.prototype.hideSelectionGroup = function(groupId, status) {
		if(status === undefined)
			return;

		if(_activeStateSelections[groupId].hidden !== status){
			_activeStateSelections[groupId].hidden = status;
			_callbacks.hideNodes(_activeStateSelections[groupId].nodes, status);
		}
	};

	Graphbrowser.Modules.SelectionTools.prototype.fixateSelectionGroup = function(groupId, status) {
		if(status === undefined)
			return;
		if(_activeStateSelections[groupId].fixed !== status){
			_activeStateSelections[groupId].fixed = status;
			$.each(_activeStateSelections[groupId].nodes, function(i, node){
				$.each(_s.graph.nodes(), function(key, gnode){
					if(node.id === gnode.id){
						gnode.fixed = _activeStateSelections[groupId].fixed;
						node.fixed = _activeStateSelections[groupId].fixed;
					}
				});
			});
		}
	};

	Graphbrowser.Modules.SelectionTools.prototype.clearSelectionGroup = function(groupId) {
		var self = this;
		self.onHideSelectionGroup(groupId, false);
		self.setSelectionFixed(groupId, false);
		_callbacks.sigmaPlugins.activeState.dropNodes();
		_activeStateSelections[groupId].nodes = undefined;
		_s.refresh({skipIndexation: true})
	};

	Graphbrowser.Modules.SelectionTools.prototype.deleteSelectionGroup = function(groupId) {
		var self = this;
		self.clearSelectionGroup();
		_activeStateSelections.splice(_activeStateSelections.indexof(groupId), 1);
	};

	Graphbrowser.Modules.SelectionTools.prototype.activateSelectionGroup = function(groupId) {
		var self = this;
		if(self.isActive === false || groupId === undefined){
			return;
		}
		_currentActiveSelection = groupId;
		self.updateActiveState();
	};

	Graphbrowser.Modules.SelectionTools.prototype.updateActiveState = function() {
		var self = this;

		if(!self.isActive && _activeStateSelections[_currentActiveSelection].nodes === undefined)
			return;

		_callbacks.sigmaPlugins.activeState.dropNodes();
		var an = [];
		$.each(_activeStateSelections[_currentActiveSelection].nodes, function(i, node) {
			an.push(node.id);
		});
		_callbacks.sigmaPlugins.activeState.addNodes(an);
		_s.refresh({skipIndexation: true});
	};


	Graphbrowser.Modules.SelectionTools.prototype.activateSelectionLasso = function(status) {
		if((!_callbacks.sigmaPlugins.lasso.isActive) && status === true){
			_callbacks.sigmaPlugins.lasso.activate();
		}
		else{
			_callbacks.sigmaPlugins.lasso.deactivate();
		}
	};

	Graphbrowser.Modules.SelectionTools.prototype.deactivateSelectionTools = function(status) {
		var self = this;

		if(!self.isActive)
			return;

		self.isActive = false;

		if(_callbacks.sigmaPlugins.lasso.isActive){
			self.activateSelectionLasso(false);
		}

		_s.renderers[0].unbind('render');
		_callbacks.sigmaPlugins.activeState.dropNodes();
		_callbacks.refreshSigma(true);
	};

	Graphbrowser.Modules.SelectionTools.prototype.deactivateSelectionTools = function(status) {
		var self = this;
		self.isActive = false;

		if(_callbacks.sigmaPlugins.lasso.isActive){
			self.activateSelectionLasso(false);
		}

		_s.renderers[0].unbind('render');
		_callbacks.sigmaPlugins.activeState.dropNodes();
		_callbacks.refreshSigma(true);
	};

	Graphbrowser.Modules.SelectionTools.prototype.dropSelection = function(groupId){
		var nodes = _activeStateSelections[groupId].nodes;

		if (nodes !== undefined && nodes.length > 0) {
			_callbacks.sigmaPlugins.activeState.dropNodes();
			$.each(nodes, function(i, node){
				if(_s.graph.nodes(node.id))
					_s.graph.dropNode(node.id);
			});
			_s.refresh();

			$.each(_activeStateSelections, function(i, selection){
				var ids = [];
				$.each(selection.nodes, function(i, node){
					if(!_s.graph.nodes(node.id))
						ids.push(node.id);
				});

				$.each(ids, function(i, id){
					selection.nodes.splice(selection.nodes.indexOf(id), 1);
				});
			});
		}
	};

	Graphbrowser.Modules.SelectionTools.prototype.onRestore = function() {
		var self = this;
		_activeStateSelections = [];
		self.createNewSelectionGroup();
		if(self.isActive){
			_callbacks.sigmaPlugins.activeState.dropNodes();
			self.updateActiveState();
		}
	};

}).call(window);