/*
 * Copyright © 2010 Reinier Zwitserloot and Roel Spilker.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.ast;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

public abstract class Node {
	@Getter private Position position;
	@Getter private Node parent;
	
	public boolean isSyntacticallyValid() {
		List<SyntaxProblem> list = new ArrayList<SyntaxProblem>();
		checkSyntacticValidity(list);
		return list.isEmpty();
	}
	
	/**
	 * Checks if a given child is syntactically valid and throws an {@code AstException} otherwise.
	 * This method will <em>NOT</em> recursively call {@link #checkSyntacticValidity(List)} on the child (on purpose - that's not a good idea here).
	 * 
	 * @param child The actual child node object that will be checked.
	 * @param name The name of this node. For example, in a binary operation, {@code "left side"}.
	 * @param mandatory If this node not being there (being {@code null}) is a problem or acceptable.
	 * @param typeAssertion If the node exists, it must be an instance of this type.
	 * @throws AstException If {@code child} is {@code null} <em>AND</em> {@code mandatory} is {@code true}, or
	 *    {@code child} is not of the appropriate type as specified.
	 */
	protected void assertChildType(Node child, String name, boolean mandatory, Class<?> typeAssertion) throws AstException {
		SyntaxProblem p = checkChildValidity0(child, name, mandatory, typeAssertion);
		if (p != null) p.throwAstException();
	}
	
	/**
	 * Checks if a given child is syntactically valid; you specify exactly what is required for this to hold in the parameters.
	 * This method will recursively call {@link #checkSyntacticValidity(List)} on the child.
	 * 
	 * @param problems The problems list that any syntactic problems will be added to.
	 * @param child The actual child node object that will be checked.
	 * @param name The name of this node. For example, in a binary operation, {@code "left side"}.
	 * @param mandatory If this node not being there (being {@code null}) is a problem or acceptable.
	 * @param typeAssertion If the node exists, it must be an instance of this type.
	 */
	protected void checkChildValidity(List<SyntaxProblem> problems, Node child, String name, boolean mandatory, Class<?> typeAssertion) {
		SyntaxProblem problem = checkChildValidity0(child, name, mandatory, typeAssertion);
		if (problem != null) problems.add(problem);
		if (child != null) child.checkSyntacticValidity(problems);
	}
	
	private SyntaxProblem checkChildValidity0(Node child, String name, boolean mandatory, Class<?> typeAssertion) {
		String typeAssertionName = typeAssertion.getSimpleName().toLowerCase();
		boolean typeAssertionVowel = typeAssertionName.isEmpty();
		String nameTC;
		if (!typeAssertionVowel) {
			char c = typeAssertionName.charAt(0);
			typeAssertionVowel = (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u');
		}
		nameTC = name.isEmpty() ? "" :
				("" + Character.toTitleCase(name.charAt(0)) + name.substring(1));
		
		if (child == null) {
			if (mandatory) return new SyntaxProblem(this, String.format("Missing %s %s",
					name, typeAssertion.getSimpleName().toLowerCase()));
		} else {
			if (!typeAssertion.isInstance(child)) return new SyntaxProblem(this, String.format(
					"%s isn't a%s %s",
					nameTC, typeAssertionVowel ? "n" : "", typeAssertionName));
		}
		
		return null;
	}
	
	/**
	 * Add a {@link SyntaxProblem} to the list for each syntactic problem with your node, then call this method on your child nodes.
	 * Something like {@code a +} is not syntactically valid (It's missing second argument to binary operator), but something like
	 * {@code a + b} would be, <i>even if</i> both {@code a} and {@code b} end up being objects, which do not support the + operator.
	 * That is a semantic and not a syntactic problem.
	 */
	public abstract void checkSyntacticValidity(List<SyntaxProblem> problems);
	
	public boolean isGenerated() {
		return position.getGeneratedBy() != null;
	}
	
	public Node getGeneratedBy() {
		return position.getGeneratedBy();
	}
	
	public boolean hasParent() {
		return parent != null;
	}
	
	/**
	 * Adopts (accepts as direct child) the provided node.
	 * 
	 * @param child The node to adopt
	 * @throws IllegalStateException If {@code child} already has a parent (clone or unparent it first).
	 */
	protected void adopt(Node child) throws IllegalStateException {
		child.ensureParentless();
		child.parent = this;
	}
	
	/**
	 * Checks if this node is currently parentless.
	 * 
	 * @throws IllegalStateException if I have a parent.
	 */
	protected void ensureParentless() throws IllegalStateException {
		if (parent == null) return;
		throw new IllegalStateException(String.format(
				"I (%s) already have a parent, so you can't add me to something else; clone or unparent me first.",
				this.getClass().getName()));
	}
	
	/**
	 * Disowns a direct child (it will be parentless after this call).
	 * 
	 * @param child Child node to disown
	 * @throws IllegalStateException if {@code child} isn't a direct child of myself.
	 */
	protected void disown(Node child) throws IllegalStateException {
		ensureParentage(child);
		child.parent = null;
	}
	
	/**
	 * Checks if the provided node is a direct child of this node.
	 * 
	 * @param child This node must be a direct child of myself.
	 * @throws IllegalStateException If {@code child} isn't a direct child of myself.
	 */
	protected void ensureParentage(Node child) throws IllegalStateException {
		if (child.parent == this) return;
		
		throw new IllegalStateException(String.format(
				"Can't disown child of type %s - it isn't my child (I'm a %s)",
				child.getClass().getName(), this.getClass().getName()));
	}
	
	public abstract void accept(ASTVisitor visitor);
	
	@Override public String toString() {
//		SourcePrinter printer = new SourcePrinter();
//		this.accept(printer);
//		return printer.toString();
		if (this instanceof Literal) {
			return "[" + ((Literal)this).getRawValue() + "]";
		} else if (this instanceof BinaryExpression) {
			return "(" + ((BinaryExpression)this).getRawLeft() + " " + ((BinaryExpression)this).getOperator() + " " + ((BinaryExpression)this).getRawRight() + ")";
		} else if (this instanceof UnaryExpression) {
			return "(" + ((UnaryExpression)this).getRawOperator() + ((UnaryExpression)this).getOperand() + ")";
		} else if (this instanceof IncrementExpression && ((IncrementExpression)this).isPrefix()) {
			return "(" + (((IncrementExpression)this).isDecrement() ? "--" : "++") + ((IncrementExpression)this).getOperand() + ")";
		} else if (this instanceof IncrementExpression && !((IncrementExpression)this).isPrefix()) {
			return "(" + ((IncrementExpression)this).getOperand() + (((IncrementExpression)this).isDecrement() ? "--" : "++") + ")";
		} else if (this instanceof InlineIfExpression) {
			return String.format("(%s?%s:%s)", ((InlineIfExpression)this).getRawCondition(), ((InlineIfExpression)this).getRawIfTrue(), ((InlineIfExpression)this).getRawIfFalse());
		} else if (this instanceof Type) {
			try {
				return ((Type)this).getTypeName();
			} catch (AstException e) {
				return "<Broken type>";
			}
		} else if (this instanceof TypePart) {
			try {
				return ((TypePart)this).getTypeName();
			} catch (AstException e) {
				return "<Broken typePart>";
			}
		} else if (this instanceof Identifier) {
			return ((Identifier)this).getName();
		} else if (this instanceof Cast) {
			return "(C:" + ((Cast)this).getType() + ")_" + ((Cast)this).getOperand() + "_";
		} else {
			return "NODE: " + this.getClass().getSimpleName();
		}
	}
}
