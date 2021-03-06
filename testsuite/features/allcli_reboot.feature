# Copyright (c) 2017-2018 SUSE LLC.
# Licensed under the terms of the MIT license.

Feature: Reboot systems managed by SUSE Manager

@sshminion
  Scenario: Reboot the SSH-managed SLES minion
    Given I am on the Systems overview page of this "ssh-minion"
    When I follow first "Schedule System Reboot"
    Then I should see a "System Reboot Confirmation" text
    And I should see a "Reboot system" button
    When I click on "Reboot system"
    Then I wait and check that "ssh-minion" has rebooted

  Scenario: Schedule a reboot on a SLES Salt minion
    Given I am on the Systems overview page of this "sle-minion"
    When I follow first "Schedule System Reboot"
    Then I should see a "System Reboot Confirmation" text
    And I should see a "Reboot system" button
    And I click on "Reboot system"
    Then I should see a "Reboot scheduled for system" text

  Scenario: Reboot action is not COMPLETED until SLES minion is rebooted
    Given I am on the Systems overview page of this "sle-minion"
    When I follow "Events" in the content area
    And I follow "History" in the content area
    And I wait until I see "System reboot scheduled by admin" text, refreshing the page
    And I follow first "System reboot scheduled by admin"
    Then I should see a "This action's status is: Picked Up." text
    And I wait and check that "sle-minion" has rebooted
    Then I wait until I see "This action's status is: Completed." text, refreshing the page
    And I should see a "Reboot completed." text

  Scenario: Reboot a SLES tradional client
    Given I am on the Systems overview page of this "sle-client"
    When I follow first "Schedule System Reboot"
    Then I should see a "System Reboot Confirmation" text
    And I should see a "Reboot system" button
    When I click on "Reboot system"
    And I run "rhn_check -vvv" on "sle-client"
    Then I wait and check that "sle-client" has rebooted

@centosminion
  Scenario: Reboot the CentOS minion
    Given I am on the Systems overview page of this "ceos-minion"
    When I follow first "Schedule System Reboot"
    Then I should see a "System Reboot Confirmation" text
    And I should see a "Reboot system" button
    When I click on "Reboot system"
    Then I should see a "Reboot scheduled for system" text

@centosminion
  Scenario: Reboot action is not COMPLETED until CentOS minion is rebooted
    Given I am on the Systems overview page of this "ceos-minion"
    When I follow "Events" in the content area
    And I follow "History" in the content area
    And I wait until I see "System reboot scheduled by admin" text, refreshing the page
    And I follow first "System reboot scheduled by admin"
    Then I should see a "This action's status is: Picked Up." text
    And I wait and check that "ceos-minion" has rebooted
    Then I wait until I see "This action's status is: Completed." text, refreshing the page
    And I should see a "Reboot completed." text
