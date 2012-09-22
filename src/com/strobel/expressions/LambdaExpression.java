package com.strobel.expressions;

import com.strobel.compilerservices.CallerResolver;
import com.strobel.compilerservices.DebugInfoGenerator;
import com.strobel.core.StringUtilities;
import com.strobel.core.VerifyArgument;
import com.strobel.reflection.MethodInfo;
import com.strobel.reflection.Type;
import com.strobel.reflection.emit.MethodBuilder;
import com.strobel.reflection.emit.TypeBuilder;

import java.lang.reflect.Modifier;

/**
 * @author Mike Strobel
 */
public final class LambdaExpression<T> extends Expression {
    private final String _name;
    private final Expression _body;
    private final ParameterExpressionList _parameters;
    private final Type<T> _interfaceType;
    private final boolean _tailCall;
    private final Type _returnType;
    private final Class<?> _creationContext;

    LambdaExpression(
        final Type<T> interfaceType,
        final String name,
        final Expression body,
        final boolean tailCall,
        final ParameterExpressionList parameters) {

        if (interfaceType != null) {
            _interfaceType = interfaceType;
        }
        else {
            _interfaceType = resolveDelegateType(body, parameters);
        }

        _name = name;
        _body = body;
        _tailCall = tailCall;
        _parameters = parameters;
        _returnType = _interfaceType.getMethods().get(0).getReturnType();
        _creationContext = CallerResolver.getCallerClass(2);
    }

    @SuppressWarnings("unchecked")
    private static <T> Type<T> resolveDelegateType(final Expression body, final ParameterExpressionList parameters) {
        return (Type<T>) CustomDelegateTypeCache.get(
            body.getType(),
            parameters.getParameterTypes()
        );
    }

    @Override
    public final Type<T> getType() {
        return _interfaceType;
    }

    @Override
    public ExpressionType getNodeType() {
        return ExpressionType.Lambda;
    }

    public final String getName() {
        return _name;
    }

    public final Expression getBody() {
        return _body;
    }

    public final ParameterExpressionList getParameters() {
        return _parameters;
    }

    public final Type getReturnType() {
        return _returnType;
    }

    public final boolean isTailCall() {
        return _tailCall;
    }

    public final LambdaExpression<T> update(final Expression body, final ParameterExpressionList parameters) {
        if (body == _body && parameters == _parameters) {
            return this;
        }
        return Expression.lambda(_interfaceType, getName(), body, isTailCall(), parameters);
    }

    @Override
    protected Expression accept(final ExpressionVisitor visitor) {
        return visitor.visitLambda(this);
    }

    @SuppressWarnings("unchecked")
    final LambdaExpression<T> accept(final StackSpiller spiller) {
        return spiller.rewrite(this);
    }

    final Class<?> getCreationContext() {
        return _creationContext;
    }

    public final T compile() {
        return compileDelegate().getInstance();
    }

    public final Delegate<T> compileDelegate() {
        return LambdaCompiler.compile(this, DebugInfoGenerator.empty());
    }

    public final void compileToMethod(final MethodBuilder methodBuilder) {
        LambdaCompiler.compile(this, methodBuilder, DebugInfoGenerator.empty());
    }

    public final MethodInfo compileToMethod(final TypeBuilder<?> typeBuilder) {
        final String name = getName();

        return compileToMethod(
            typeBuilder,
            StringUtilities.isNullOrWhitespace(name)
            ? LambdaCompiler.getUniqueMethodName()
            : name,
            Modifier.PUBLIC
        );
    }

    public final MethodInfo compileToMethod(
        final TypeBuilder<?> typeBuilder,
        final String name) {

        return compileToMethod(typeBuilder, name, Modifier.PUBLIC);
    }

    public final MethodInfo compileToMethod(
        final TypeBuilder<?> typeBuilder,
        final String name,
        final int modifiers) {

        VerifyArgument.notNull(typeBuilder, "typeBuilder");
        VerifyArgument.notNullOrWhitespace(name, "name");

        final MethodInfo invokeMethod = Expression.getInvokeMethod(this);

        final MethodBuilder methodBuilder = typeBuilder.defineMethod(
            name,
            modifiers,
            invokeMethod.getReturnType(),
            invokeMethod.getParameters().getParameterTypes()
        );

        LambdaCompiler.compile(this, methodBuilder, DebugInfoGenerator.empty());

        return methodBuilder;
    }
}

