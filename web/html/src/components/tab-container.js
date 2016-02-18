"use strict";

var React = require("react");

var TabContainer = React.createClass({
  getInitialState: function() {
    return {activeTabHash: this.sanitizeHash(this.props.initialActiveTabHash)};
  },

  componentWillReceiveProps: function(nextProps) {
    this.setState({activeTabHash: this.sanitizeHash(nextProps.initialActiveTabHash)});
  },

  sanitizeHash: function(hash) {
    if (this.props.hashes.indexOf(hash) >= 0) {
      return hash;
    }
    return this.props.hashes[0];
  },

  onActiveTabChange: function(hash) {
    this.setState({activeTabHash: hash});
    if (this.props.onTabHashChange) {
      this.props.onTabHashChange(hash);
    }
  },

  render: function() {
    var tabLabels = this.props.hashes.map((hash, i) => {
      const label = this.props.labels[i];
      return <TabLabel onClick={() => this.onActiveTabChange(hash)} text={label} active={this.state.activeTabHash == hash} hash={hash} />;
    });

    return (
      <div>
        <div className="spacewalk-content-nav">
          <ul className="nav nav-tabs">
            {tabLabels}
          </ul>
        </div>
        {this.props.panels[this.props.hashes.indexOf(this.state.activeTabHash)]}
      </div>
    );
  }
});

var TabLabel = React.createClass({
  render: function() {
    return(
      <li className={this.props.active ? "active" : ""}>
        <a href={this.props.hash} onClick={this.props.onClick}>{this.props.text}</a>
      </li>
    );
  }
});

module.exports = {
    TabContainer : TabContainer,
    TabLabel : TabLabel
}
