
package org.joget.marketplace.model;

import java.util.List;

public class GuestToken {

    private List<Resource> resources;
    private List<Object> rls;
    private SupersetUser user;

    public List<Resource> getResources() {
        return resources;
    }

    public void setResources(List<Resource> resources) {
        this.resources = resources;
    }

    public List<Object> getRls() {
        return rls;
    }

    public void setRls(List<Object> rls) {
        this.rls = rls;
    }

    public SupersetUser getUser() {
        return user;
    }

    public void setUser(SupersetUser user) {
        this.user = user;
    }
}
