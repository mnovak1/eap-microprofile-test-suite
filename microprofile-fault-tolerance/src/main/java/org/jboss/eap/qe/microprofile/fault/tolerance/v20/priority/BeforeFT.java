package org.jboss.eap.qe.microprofile.fault.tolerance.v20.priority;

import javax.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding
public @interface BeforeFT {
}
