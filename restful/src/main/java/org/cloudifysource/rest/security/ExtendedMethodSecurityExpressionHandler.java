/*******************************************************************************
 * Copyright (c) 2011 GigaSpaces Technologies Ltd. All rights reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.cloudifysource.rest.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aopalliance.intercept.MethodInvocation;
import org.cloudifysource.dsl.rest.ApplicationDescription;
import org.cloudifysource.rest.util.RestUtils;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.ExpressionUtils;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;

/**
 * 
 * @author noak
 * @since 2.3.1
 *
 */
public class ExtendedMethodSecurityExpressionHandler extends
		DefaultMethodSecurityExpressionHandler implements
		MethodSecurityExpressionHandler {

	private ParameterNameDiscoverer parameterNameDiscoverer = new LocalVariableTableParameterNameDiscoverer();
	// private PermissionEvaluator permissionEvaluator = new
	// DenyAllPermissionEvaluator();
	private PermissionEvaluator permissionEvaluator = new CustomDenyAllPermissionEvaluator();
	private AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
	private ExpressionParser expressionParser = new SpelExpressionParser();
	private RoleHierarchy roleHierarchy;

	/**
	 * Uses a {@link CustomMethodSecurityEvaluationContext} as the
	 * <tt>EvaluationContext</tt> implementation and configures it with a
	 * {@link CustomMethodSecurityExpressionRoot} instance as the expression
	 * root object.
	 */
	@Override
	public EvaluationContext createEvaluationContext(Authentication auth,
			MethodInvocation mi) {
		CustomMethodSecurityEvaluationContext ctx = new CustomMethodSecurityEvaluationContext(
				auth, mi, parameterNameDiscoverer);
		CustomMethodSecurityExpressionRoot root = new CustomMethodSecurityExpressionRoot(
				auth);
		root.setTrustResolver(trustResolver);
		root.setPermissionEvaluator(permissionEvaluator);
		root.setRoleHierarchy(roleHierarchy);
		ctx.setRootObject(root);

		return ctx;
	}
	
	@Override
	public ExpressionParser getExpressionParser() {
        return expressionParser;
    }

    @Override
	public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
        this.parameterNameDiscoverer = parameterNameDiscoverer;
    }

    @Override
	public void setPermissionEvaluator(PermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    @Override
	public void setTrustResolver(AuthenticationTrustResolver trustResolver) {
        this.trustResolver = trustResolver;
    }

    @Override
	public void setReturnObject(Object returnObject, EvaluationContext ctx) {
        ((CustomMethodSecurityExpressionRoot)ctx.getRootObject().getValue()).setReturnObject(returnObject);
    }

    @Override
	public void setRoleHierarchy(RoleHierarchy roleHierarchy) {
        this.roleHierarchy = roleHierarchy;
    }

	@Override
	@SuppressWarnings("unchecked")
	public Object filter(Object filterTarget, Expression filterExpression,
			EvaluationContext ctx) {
		CustomMethodSecurityExpressionRoot rootObject = (CustomMethodSecurityExpressionRoot) ctx
				.getRootObject().getValue();

		if (logger.isDebugEnabled()) {
			logger.debug("Filtering with expression: "
					+ filterExpression.getExpressionString());
		}

		if (filterTarget instanceof Collection ||
				filterTarget.getClass().isArray()) {
			return super.filter(filterTarget, filterExpression, ctx);
		}

		if (filterTarget instanceof Map<?, ?>) {
			Map<String, Object> returnValue = (Map<String, Object>) filterTarget;

			if (returnValue.get(RestUtils.STATUS_KEY).equals(RestUtils.SUCCESS)) {
				Object responseObject = returnValue.get(RestUtils.RESPONSE_KEY);
				if (responseObject != null && responseObject instanceof Map) {
					Map<String, String> objectsMap = (Map<String, String>) responseObject;
					Map<Object, String> retainMap = new HashMap<Object, String>();
					
					for (Map.Entry<String, String> entry : objectsMap.entrySet()) {
						String filterObject = entry.getValue();
						rootObject.setFilterObject(filterObject);
						if (ExpressionUtils.evaluateAsBoolean(filterExpression, ctx)) {
							retainMap.put(entry.getKey(), entry.getValue());
						}
					}
					returnValue = RestUtils.successStatus(retainMap);
					
				} else if (responseObject != null && responseObject instanceof List) {
					List<Object> objectsList = (List<Object>) responseObject;
					List<Object> retainList = new ArrayList<Object>();
					
					for (Object object : objectsList) {
						if (object instanceof ApplicationDescription) {
							rootObject.setFilterObject(((ApplicationDescription)object).getAuthGroups());
							if (ExpressionUtils.evaluateAsBoolean(filterExpression, ctx)) {
								retainList.add(object);
							}
						}
						
					}
					returnValue = RestUtils.successStatus(retainList);
				}
			}
			
			return returnValue;
		}

		throw new IllegalArgumentException(
				"Filter target must be a collection or array type, but was "
						+ filterTarget);
	}
}