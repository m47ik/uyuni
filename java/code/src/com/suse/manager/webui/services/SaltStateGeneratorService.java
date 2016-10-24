/**
 * Copyright (c) 2016 SUSE LLC
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
package com.suse.manager.webui.services;

import static com.suse.manager.webui.services.SaltConstants.PILLAR_DATA_FILE_EXT;
import static com.suse.manager.webui.services.SaltConstants.PILLAR_DATA_FILE_PREFIX;
import static com.suse.manager.webui.services.SaltConstants.SALT_CUSTOM_STATES_DIR;
import static com.suse.manager.webui.services.SaltConstants.SALT_SERVER_STATE_FILE_PREFIX;
import static com.suse.manager.webui.services.SaltConstants.SUMA_PILLAR_DATA_PATH;
import static com.suse.manager.webui.services.SaltConstants.SUMA_STATE_FILES_ROOT_PATH;
import static com.suse.manager.webui.utils.SaltFileUtils.defaultExtension;

import com.redhat.rhn.common.conf.ConfigDefaults;
import com.redhat.rhn.domain.channel.Channel;
import com.redhat.rhn.domain.org.Org;
import com.redhat.rhn.domain.server.ManagedServerGroup;
import com.redhat.rhn.domain.server.MinionServer;
import com.redhat.rhn.domain.server.MinionServerFactory;
import com.redhat.rhn.domain.server.Server;
import com.redhat.rhn.domain.server.ServerGroup;
import com.redhat.rhn.domain.server.ServerGroupFactory;
import com.redhat.rhn.domain.server.ServerPath;
import com.redhat.rhn.domain.state.OrgStateRevision;
import com.redhat.rhn.domain.state.ServerGroupStateRevision;
import com.redhat.rhn.domain.state.ServerStateRevision;
import com.redhat.rhn.domain.state.StateFactory;
import com.redhat.rhn.domain.state.StateRevision;
import com.redhat.rhn.domain.user.User;

import com.suse.manager.utils.MachinePasswordUtils;
import com.suse.manager.webui.controllers.StatesAPI;
import com.suse.manager.webui.services.impl.SaltService;
import com.suse.manager.webui.utils.SaltCustomState;
import com.suse.manager.webui.utils.SaltPillar;
import com.suse.manager.webui.utils.TokenBuilder;

import org.apache.log4j.Logger;
import org.jose4j.lang.JoseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service to manage the Salt states generated by Suse Manager.
 */
public enum SaltStateGeneratorService {

    // Singleton instance of this class
    INSTANCE;

    private static final String PKGSET_COOKIE_PATH = "/var/cache/salt/minion/rpmdb.cookie";
    private static final int PKGSET_INTERVAL = 5;
    private static final  Map<String, Object> PKGSET_BEACON_CONFIG;
    static {
        PKGSET_BEACON_CONFIG = new HashMap<>();
        Map<String, Object> pkgSetBeaconProps = new HashMap<>();
        pkgSetBeaconProps.put("cookie", PKGSET_COOKIE_PATH);
        pkgSetBeaconProps.put("interval",  PKGSET_INTERVAL);
        PKGSET_BEACON_CONFIG.put("pkgset", pkgSetBeaconProps);
    }

    /** Logger */
    private static final Logger LOG = Logger.getLogger(SaltStateGeneratorService.class);

    private Path suseManagerStatesFilesRoot;

    private Path pillarDataPath;

    SaltStateGeneratorService() {
        suseManagerStatesFilesRoot = Paths.get(SUMA_STATE_FILES_ROOT_PATH);
        pillarDataPath = Paths.get(SUMA_PILLAR_DATA_PATH);
    }

    /**
     * Generate server specific pillar if the given server is a minion.
     * @param minion the minion server
     */
    public void generatePillar(MinionServer minion) {
        LOG.debug("Generating pillar file for minion: " + minion.getMinionId());

        List<ManagedServerGroup> groups = ServerGroupFactory.listManagedGroups(minion);
        List<Long> groupIds = groups.stream()
                .map(g -> g.getId()).collect(Collectors.toList());
        SaltPillar pillar = new SaltPillar();
        pillar.add("org_id", minion.getOrg().getId());
        pillar.add("group_ids", groupIds.toArray(new Long[groupIds.size()]));

        pillar.add("machine_password", MachinePasswordUtils.machinePassword(minion));

        Map<String, Object> chanPillar = new HashMap<>();
        try {
            TokenBuilder tokenBuilder = new TokenBuilder(minion.getOrg().getId());
            tokenBuilder.useServerSecret();
            String token = tokenBuilder.getToken();

            for (Channel chan : minion.getChannels()) {
                Map<String, Object> chanProps = new HashMap<>();
                chanProps.put("alias", "susemanager:" + chan.getLabel());
                chanProps.put("name", chan.getName());
                chanProps.put("enabled", "1");
                chanProps.put("autorefresh", "1");
                chanProps.put("host", getChannelHost(minion));
                chanProps.put("token", token);
                chanProps.put("type", "rpm-md");
                chanProps.put("gpgcheck", "0");
                chanProps.put("repo_gpgcheck", "0");
                chanProps.put("pkg_gpgcheck", "1");

                chanPillar.put(chan.getLabel(), chanProps);

            }
            pillar.add("channels", chanPillar);

            // this add the configuration for the beacon that tell us when the
            // minion packages are modified locally
            if (minion.getOs().toLowerCase().equals("sles") ||
                    minion.getOsFamily().toLowerCase().equals("redhat")) {
                pillar.add("beacons", PKGSET_BEACON_CONFIG);
            }
        }
        catch (JoseException e) {
            LOG.error(String.format(
                    "Generating pillar for server with serverId '%s' failed.",
                    minion.getId()), e);
        }

        try {
            Files.createDirectories(pillarDataPath);
            Path filePath = pillarDataPath.resolve(
                    getServerPillarFileName(minion)
            );
            com.suse.manager.webui.utils.SaltStateGenerator saltStateGenerator =
                    new com.suse.manager.webui.utils.SaltStateGenerator(filePath.toFile());
            saltStateGenerator.generate(pillar);
        }
        catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    /**
     * Return the channel hostname for a given server.
     *
     * @param server server to get the channel host for.
     * @return channel hostname.
     */
    public static String getChannelHost(Server server) {
        Optional<ServerPath> path = server.getFirstServerPath();
        if (!path.isPresent()) {
            // client is not proxied, return this server's hostname
            // HACK: we currently have no better way
            return ConfigDefaults.get().getCobblerHost();
        }
        else {
            return path.get().getHostname();
        }
    }

    private String getServerPillarFileName(MinionServer minion) {
        return PILLAR_DATA_FILE_PREFIX + "_" +
            minion.getMinionId() + "." +
                PILLAR_DATA_FILE_EXT;
    }

    /**
     * Remove the corresponding pillar data if the server is a minion.
     * @param minion the minion server
     */
    public void removePillar(MinionServer minion) {
        LOG.debug("Removing pillar file for minion: " + minion.getMinionId());
        Path filePath = pillarDataPath.resolve(
                getServerPillarFileName(minion));
        try {
            Files.deleteIfExists(filePath);
        }
        catch (IOException e) {
            LOG.error("Could not remove pillar file " + filePath);
        }
    }

    /**
     * Remove the custom states assignments for minion server.
     * @param minion the minion server
     */
    public void removeCustomStateAssignments(MinionServer minion) {
        removeCustomStateAssignments(getServerStateFileName(minion.getMachineId()));
    }

    /**
     * Remove the custom states assignments for server group.
     * @param group the server group
     */
    public void removeCustomStateAssignments(ServerGroup group) {
        removeCustomStateAssignments(getGroupStateFileName(group.getId()));
    }

    /**
     * Remove the custom states assignments for an organization.
     * @param org the organization
     */
    public void removeCustomStateAssignments(Org org) {
        removeCustomStateAssignments(getOrgStateFileName(org.getId()));
    }

    private void removeCustomStateAssignments(String file) {
        Path baseDir = suseManagerStatesFilesRoot.resolve(SALT_CUSTOM_STATES_DIR);
        Path filePath = baseDir.resolve(defaultExtension(file));

        try {
            Files.deleteIfExists(filePath);
        }
        catch (IOException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Generate .sls file to assign custom states to a server.
     * @param serverStateRevision the state revision of a server
     */
    public void generateServerCustomState(ServerStateRevision serverStateRevision) {
        serverStateRevision.getServer().asMinionServer().ifPresent(minion -> {
            LOG.debug("Generating custom state SLS file for server: " + minion.getId());

            generateCustomStates(minion.getOrg().getId(), serverStateRevision,
                    getServerStateFileName(minion.getMachineId()),
                    suseManagerStatesFilesRoot);
        });
    }

    /**
     * Generate .sls file to assign custom states to a server group.
     * @param groupStateRevision the state revision of a server group
     */
    public void generateGroupCustomState(ServerGroupStateRevision groupStateRevision) {
        generateGroupCustomState(groupStateRevision, suseManagerStatesFilesRoot);
    }

    /**
     * Generate .sls file to assign custom states to a server group.
     * @param groupStateRevision the state revision of a server group
     * @param statePath the directory where to generate the files
     */
    public void generateGroupCustomState(ServerGroupStateRevision groupStateRevision,
                                         Path statePath) {
        ServerGroup group = groupStateRevision.getGroup();
        LOG.debug("Generating custom state SLS file for server group: " + group.getId());

        generateCustomStates(group.getOrg().getId(), groupStateRevision,
                getGroupStateFileName(group.getId()), statePath);
    }


    /**
     * Generate .sls file to assign custom states to an org.
     * @param orgStateRevision the state revision of an org
     */
    public void generateOrgCustomState(OrgStateRevision orgStateRevision) {
        generateOrgCustomState(orgStateRevision, suseManagerStatesFilesRoot);
    }

    /**
     * Generate .sls file to assign custom states to an org.
     * @param orgStateRevision the state revision of an org
     * @param statePath the directory where to generate the sls files
     */
    public void generateOrgCustomState(OrgStateRevision orgStateRevision, Path statePath) {
        Org org = orgStateRevision.getOrg();
        LOG.debug("Generating custom state SLS file for organization: " + org.getId());

        generateCustomStates(org.getId(), orgStateRevision,
                getOrgStateFileName(org.getId()), statePath);
    }

    private void generateCustomStates(long orgId, StateRevision stateRevision,
                                      String fileName, Path statePath) {
        Set<String> stateNames = stateRevision.getCustomStates()
                .stream()
                .filter(s-> !s.isDeleted()) // skip deleted states
                .map(s -> s.getStateName())
                .collect(Collectors.toSet());

        generateCustomStateAssignmentFile(orgId, fileName, stateNames, statePath);
    }

    private void generateCustomStateAssignmentFile(long orgId, String fileName,
        Set<String> stateNames, Path statePath) {
        stateNames = SaltService.INSTANCE.resolveOrgStates(
                orgId, stateNames);

        Path baseDir = statePath.resolve(SALT_CUSTOM_STATES_DIR);
        try {
            Files.createDirectories(baseDir);
            Path filePath = baseDir.resolve(defaultExtension(fileName));
            com.suse.manager.webui.utils.SaltStateGenerator saltStateGenerator =
                    new com.suse.manager.webui.utils.SaltStateGenerator(filePath.toFile());
            saltStateGenerator.generate(new SaltCustomState(stateNames));
        }
        catch (IOException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Generate pillar and custom states assignments for a
     * newly registered server.
     * @param minion newly registered minion
     */
    public void registerServer(MinionServer minion) {
        // TODO create an empty revision ?
        generatePillar(minion);
        generateCustomStateAssignmentFile(minion.getOrg().getId(),
                getServerStateFileName(minion.getMachineId()),
                Collections.emptySet(), suseManagerStatesFilesRoot);
    }

    /**
     * Remove pillars and custom states assignments of a server.
     * @param minion the minion
     */
    public void removeServer(MinionServer minion) {
        removePillar(minion);
        removeCustomStateAssignments(minion);
    }

    /**
     * Remove custom states assignments of a group.
     * @param group the group
     */
    public void removeServerGroup(ServerGroup group) {
        removeCustomStateAssignments(group);
    }

    /**
     * Remove custom states assignments of all servers in that org.
     * @param org the org
     */
    public void removeOrg(Org org) {
        MinionServerFactory.lookupByOrg(org.getId()).stream()
                .forEach(this::removeServer);
        removeCustomStateAssignments(org);
    }

    /**
     * Regenerate custom state assignments for org, group and severs where
     * the given state is used.
     * @param orgId org id
     * @param name custom state name
     */
    public void regenerateCustomStates(long orgId, String name) {
        StateFactory.CustomStateRevisionsUsage usage = StateFactory
                .latestStateRevisionsByCustomState(orgId, name);
        regenerateCustomStates(usage);
    }

    /**
     * Regenerate custom state assignments for org, group and severs for
     * the given usages.
     * @param usage custom states usages
     */
    public void regenerateCustomStates(StateFactory.CustomStateRevisionsUsage usage) {
        usage.getServerStateRevisions().forEach(rev ->
                generateServerCustomState(rev)
        );
        usage.getServerGroupStateRevisions().forEach(rev ->
                generateGroupCustomState(rev)
        );
        usage.getOrgStateRevisions().forEach(rev ->
                generateOrgCustomState(rev)
        );
    }

    /**
     * Regenerate pillar with the new org and create a new state revision without
     * any package or custom states.
     * @param minion the migrated server
     * @param user the user performing the migration
     */
    public void migrateServer(MinionServer minion, User user) {
        // generate a new state revision without any package or custom states
        ServerStateRevision newStateRev = StateRevisionService.INSTANCE
                .cloneLatest(minion, user, false, false);
        StateFactory.save(newStateRev);

        // refresh pillar, custom and package states
        generatePillar(minion);
        generateServerCustomState(newStateRev);
        StatesAPI.generateServerPackageState(minion);
    }

    private String getGroupStateFileName(long groupId) {
        return "group_" + groupId;
    }

    private String getOrgStateFileName(long orgId) {
        return "org_" + orgId;
    }


    private String getServerStateFileName(String digitalServerId) {
        return SALT_SERVER_STATE_FILE_PREFIX + digitalServerId;
    }


    /**
     * @param groupId the id of the server group
     * @return the name of the generated server group .sls file.
     */
    public String getServerGroupGeneratedStateName(long groupId) {
        return SALT_CUSTOM_STATES_DIR + "." + getGroupStateFileName(groupId);
    }

    /**
     * @param generatedSlsRootIn the root path where state files are generated
     */
    public void setSuseManagerStatesFilesRoot(Path generatedSlsRootIn) {
        this.suseManagerStatesFilesRoot = generatedSlsRootIn;
    }

    /**
     * @param pillarDataPathIn the root path where pillar files are generated
     */
    public void setPillarDataPath(Path pillarDataPathIn) {
        this.pillarDataPath = pillarDataPathIn;
    }

    /**
     * Generate state files for a new server group.
     * @param serverGroup the new server group
     */
    public void createServerGroup(ServerGroup serverGroup) {
        generateCustomStateAssignmentFile(serverGroup.getOrg().getId(),
                getGroupStateFileName(serverGroup.getId()),
                Collections.emptySet(), suseManagerStatesFilesRoot);
    }

    /**
     * Generate state files for a new org.
     * @param org the new org
     */
    public void createOrg(Org org) {
        generateCustomStateAssignmentFile(org.getId(),
                getOrgStateFileName(org.getId()),
                Collections.emptySet(), suseManagerStatesFilesRoot);
    }


}
