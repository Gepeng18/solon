package org.noear.solon.cloud.extend.local.service;

import org.noear.solon.Utils;
import org.noear.solon.cloud.CloudConfigHandler;
import org.noear.solon.cloud.exception.CloudConfigException;
import org.noear.solon.cloud.model.Config;
import org.noear.solon.cloud.service.CloudConfigService;

import java.io.IOException;

/**
 * @author noear
 * @since 1.10
 */
public class CloudConfigServiceLocalImpl implements CloudConfigService {
    @Override
    public Config pull(String group, String name) {
        String configKey = String.format("META-INF/cloud/config/%s/%s");

        try {
            String value = Utils.getResourceAsString(configKey);

            return new Config(group, name, value, 0);
        } catch (IOException e) {
            throw new CloudConfigException(e);
        }
    }

    @Override
    public boolean push(String group, String name, String value) {
        return false;
    }

    @Override
    public boolean remove(String group, String name) {
        return false;
    }

    @Override
    public void attention(String group, String name, CloudConfigHandler observer) {

    }
}
