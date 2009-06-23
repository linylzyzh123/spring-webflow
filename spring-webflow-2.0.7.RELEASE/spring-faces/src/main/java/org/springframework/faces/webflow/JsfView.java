/*
 * Copyright 2004-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.faces.webflow;

import java.io.IOException;

import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import javax.faces.event.PhaseId;
import javax.faces.lifecycle.Lifecycle;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.faces.ui.AjaxViewRoot;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;
import org.springframework.webflow.execution.View;

/**
 * JSF-specific {@link View} implementation.
 * 
 * @author Jeremy Grelle
 */
public class JsfView implements View {

	private static final Log logger = LogFactory.getLog(JsfView.class);

	public static final String EVENT_KEY = "org.springframework.webflow.FacesEvent";

	private UIViewRoot viewRoot;

	private Lifecycle facesLifecycle;

	private RequestContext requestContext;

	private String viewId;

	/**
	 * Creates a new JSF view.
	 * @param viewRoot the view root
	 * @param facesLifecycle the flow faces lifecycle
	 * @param context the current flow request
	 */
	public JsfView(UIViewRoot viewRoot, Lifecycle facesLifecycle, RequestContext context) {
		this.viewRoot = viewRoot;
		this.viewId = viewRoot.getViewId();
		this.facesLifecycle = facesLifecycle;
		this.requestContext = context;
	}

	/**
	 * Returns the underlying view root.
	 * @return the view root
	 */
	public UIViewRoot getViewRoot() {
		return this.viewRoot;
	}

	public void setViewRoot(UIViewRoot viewRoot) {
		this.viewRoot = viewRoot;
	}

	/**
	 * Performs the standard duties of the JSF RENDER_RESPONSE phase.
	 */
	public void render() throws IOException {
		FacesContext facesContext = FlowFacesContext.newInstance(requestContext, facesLifecycle);
		if (facesContext.getResponseComplete()) {
			return;
		}
		facesContext.setViewRoot(viewRoot);
		try {
			JsfUtils.notifyBeforeListeners(PhaseId.RENDER_RESPONSE, facesLifecycle, facesContext);
			logger.debug("Asking view handler to render view");
			facesContext.getApplication().getViewHandler().renderView(facesContext, viewRoot);
			JsfUtils.notifyAfterListeners(PhaseId.RENDER_RESPONSE, facesLifecycle, facesContext);
		} finally {
			logger.debug("View rendering complete");
			facesContext.responseComplete();
			facesContext.release();
		}
	}

	public boolean userEventQueued() {
		return requestContext.getRequestParameters().size() > 1;
	}

	/**
	 * Executes postback-processing portions of the standard JSF lifecycle including APPLY_REQUEST_VALUES through
	 * INVOKE_APPLICATION.
	 */
	public void processUserEvent() {
		FacesContext facesContext = FlowFacesContext.newInstance(requestContext, facesLifecycle);
		facesContext.setViewRoot(viewRoot);
		try {
			// Must respect these flags in case user set them during RESTORE_VIEW phase
			if (!facesContext.getRenderResponse() && !facesContext.getResponseComplete()) {
				facesLifecycle.execute(facesContext);
			}
		} finally {
			facesContext.release();
		}
	}

	/**
	 * Updates the component state stored in View scope so that it remains in sync with the updated flow execution
	 * snapshot
	 */
	public void saveState() {
		FacesContext facesContext = FlowFacesContext.newInstance(requestContext, facesLifecycle);
		if (viewRoot instanceof AjaxViewRoot) {
			facesContext.setViewRoot(((AjaxViewRoot) viewRoot).getOriginalViewRoot());
		} else {
			facesContext.setViewRoot(viewRoot);
		}
		try {
			facesContext.getApplication().getStateManager().saveView(facesContext);
		} finally {
			facesContext.release();
		}
	}

	public Object getUserEventState() {
		// Set the temporary UIViewRoot state so that it will be available across the redirect
		return new ViewRootHolder(getViewRoot());
	}

	public boolean hasFlowEvent() {
		return requestContext.getExternalContext().getRequestMap().contains(EVENT_KEY);
	}

	public Event getFlowEvent() {
		return new Event(this, getEventId());
	}

	public String toString() {
		return "[JSFView = '" + viewId + "']";
	}

	// internal helpers

	private String getEventId() {
		return (String) requestContext.getExternalContext().getRequestMap().get(EVENT_KEY);
	}
}