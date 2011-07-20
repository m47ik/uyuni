# Copyright (c) 2010-2011 Novell, Inc.
# Licensed under the terms of the MIT license.

When /^I execute ncc\-sync "([^"]*)"$/ do |arg1|
    $sshout = ""
    $sshout = `echo | ssh -l root -o StrictHostKeyChecking=no $TESTHOST mgr-ncc-sync #{arg1} 2>&1`
    if ! $?.success?
        raise "Execute command failed: #{$!}: #{$sshout}"
    end
end

When /^I execute mgr\-bootstrap "([^"]*)"$/ do |arg1|
    arch=`uname -m`
    arch.chomp!
    if arch != "x86_64"
        arch = "i586"
    end
    $sshout = ""
    $sshout = `echo | ssh -l root -o StrictHostKeyChecking=no $TESTHOST mgr-bootstrap --activation-keys=1-SUSE-PKG-#{arch} #{arg1} 2>&1`
    if ! $?.success?
        raise "Execute command failed: #{$!}: #{$sshout}"
    end
end

When /^I fetch "([^"]*)" from server$/ do |arg1|
    output = `curl -SkO http://$TESTHOST/#{arg1}`
    if ! $?.success?
	raise "Execute command failed: #{$!}: #{output}"
    end
end

When /^I execute "([^"]*)"$/ do |arg1|
    output = `sh ./#{arg1} 2>&1`
    if ! $?.success?
	raise "Execute command (#{arg1}) failed(#{$?}): #{$!}: #{output}"
    end
end

Then /^I want to get "([^"]*)"$/ do |arg1|
    found = false
    $sshout.each_line() do |line|
        if line.include?(arg1)
            found = true
            break
        end
    end
    if not found
        raise "'#{arg1}' not found in output '#{$sshout}'"
    end
end

Then /^I restart the spacewalk service$/ do
    $sshout = ""
    $sshout = `echo | ssh -l root -o StrictHostKeyChecking=no $TESTHOST spacewalk-service restart 2>&1`
    if ! $?.success?
        raise "Execute command failed: #{$!}: #{$sshout}"
    end
end
