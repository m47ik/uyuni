/**
 * Copyright (c) 2018 SUSE LLC
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.rhn.domain.server;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * This class holds information related to the Ids of a minion.
 */
public class MinionIds {

    private Long serverId;
    private String minionId;
    private String digitalServerId;
    private String machineId;

    /**
     * Convenience constructor from a MinionServer instance.
     *
     * @param minion the minion
     */
    public MinionIds(MinionServer minion) {
        this(minion.getId(), minion.getMinionId(), minion.getDigitalServerId(), minion.getMachineId());
    }

    /**
     * Standard constructor.
     *
     * @param serverIdIn the server id
     * @param minionIdIn the minion id
     * @param digitalServerIdIn the digital server id
     * @param machineIdIn the machine id
     */
    public MinionIds(Long serverIdIn, String minionIdIn, String digitalServerIdIn, String machineIdIn) {
        this.serverId = serverIdIn;
        this.minionId = minionIdIn;
        this.digitalServerId = digitalServerIdIn;
        this.machineId = machineIdIn;
    }

    /**
     * @return the server id
     */
    public Long getServerId() {
        return serverId;
    }

    /**
     * @return the minion id
     */
    public String getMinionId() {
        return minionId;
    }

    /**
     * @return the digital server id
     */
    public String getDigitalServerId() {
        return digitalServerId;
    }

    /**
     * @return the machine id
     */
    public String getMachineId() {
        return machineId;
    }

    @Override
    public boolean equals(Object in) {
        if (this == in) {
            return true;
        }
        if (in == null || getClass() != in.getClass()) {
            return false;
        }
        MinionIds minionIds = (MinionIds) in;

        return new EqualsBuilder()
                .append(serverId, minionIds.serverId)
                .append(minionId, minionIds.minionId)
                .append(digitalServerId, minionIds.digitalServerId)
                .append(machineId, minionIds.machineId)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(serverId)
                .append(minionId)
                .append(digitalServerId)
                .append(machineId)
                .toHashCode();
    }
}
