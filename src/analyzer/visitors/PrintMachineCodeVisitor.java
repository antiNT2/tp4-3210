package analyzer.visitors;

import analyzer.ast.*;

import java.io.PrintWriter;
import java.util.*;

public class PrintMachineCodeVisitor implements ParserVisitor
{
    private PrintWriter m_writer = null;

    private int MAX_REGISTERS_COUNT = 256;

    private final ArrayList<String> RETURNS = new ArrayList<>();
    private final ArrayList<MachineCodeLine> CODE = new ArrayList<>();

    private final ArrayList<String> MODIFIED = new ArrayList<>();
    private final ArrayList<String> REGISTERS = new ArrayList<>();

    private final HashMap<String, String> OPERATIONS = new HashMap<>();

    public PrintMachineCodeVisitor(PrintWriter writer)
    {
        m_writer = writer;

        OPERATIONS.put("+", "ADD");
        OPERATIONS.put("-", "MIN");
        OPERATIONS.put("*", "MUL");
        OPERATIONS.put("/", "DIV");
    }

    @Override
    public Object visit(SimpleNode node, Object data)
    {
        return null;
    }

    @Override
    public Object visit(ASTProgram node, Object data)
    {
        node.childrenAccept(this, null);

        computeLifeVar();
        computeNextUse();

        printMachineCode();

        return null;
    }

    @Override
    public Object visit(ASTNumberRegister node, Object data)
    {
        MAX_REGISTERS_COUNT = ((ASTIntValue) node.jjtGetChild(0)).getValue();
        return null;
    }

    @Override
    public Object visit(ASTReturnStmt node, Object data)
    {
        for (int i = 0; i < node.jjtGetNumChildren(); i++)
        {
            RETURNS.add(((ASTIdentifier) node.jjtGetChild(i)).getValue());
        }
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data)
    {
        node.childrenAccept(this, null);
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data)
    {
        node.childrenAccept(this, null);
        return null;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data)
    {
        // TODO (ex1): Modify CODE to add the needed MachLine.
        // Here the type of Assignment is "assigned = left op right".
        // You can pass null as data to children.

        String assign = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String left = (String) node.jjtGetChild(1).jjtAccept(this, null);
        String right = (String) node.jjtGetChild(2).jjtAccept(this, null);
        String op = node.getOp();

        this.CODE.add(new MachineCodeLine(op, assign, left, right));

        return null;
    }

    @Override
    public Object visit(ASTAssignUnaryStmt node, Object data)
    {
        // TODO (ex1): Modify CODE to add the needed MachLine.
        // Here the type of Assignment is "assigned = - right".
        // Suppose the left part to be the constant "#O".
        // You can pass null as data to children.

        String assign = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String left = "#0";
        String right = (String) node.jjtGetChild(1).jjtAccept(this, null);
        String op = "-";

        this.CODE.add(new MachineCodeLine(op, assign, left, right));

        return null;
    }

    @Override
    public Object visit(ASTAssignDirectStmt node, Object data)
    {
        // TODO (ex1): Modify CODE to add the needed MachLine.
        // Here the type of Assignment is "assigned = right".
        // Suppose the left part to be the constant "#O".
        // You can pass null as data to children.

        String assign = (String) node.jjtGetChild(0).jjtAccept(this, null);
        String left = "#0";
        String right = (String) node.jjtGetChild(1).jjtAccept(this, null);
        String op = "";

        this.CODE.add(new MachineCodeLine(op, assign, left, right));

        return null;
    }

    @Override
    public Object visit(ASTExpr node, Object data)
    {
        return node.jjtGetChild(0).jjtAccept(this, null);
    }

    @Override
    public Object visit(ASTIntValue node, Object data)
    {
        return "#" + node.getValue();
    }

    @Override
    public Object visit(ASTIdentifier node, Object data)
    {
        return node.getValue();
    }

    private void computeLifeVar()
    {
        // TODO (ex2): Implement life variables algorithm on the CODE array.

        MachineCodeLine lastNode = CODE.get(CODE.size() - 1);

        for (int i = 0; i < RETURNS.size(); i++)
        {
            lastNode.Life_OUT.add(RETURNS.get(i));
        }

        for (int i = CODE.size() - 1; i >= 0; i--)
        {
            MachineCodeLine currentNode = CODE.get(i);
            if (i < (CODE.size() - 1))
            {
                currentNode.Life_OUT = CODE.get(i + 1).Life_IN;
            }

            HashSet<String> newSet = new HashSet<>(currentNode.Life_OUT);

            newSet.removeAll(currentNode.DEF);
            newSet.addAll(currentNode.REF);


            currentNode.Life_IN = newSet;
        }
    }

    private void computeNextUse()
    {
        // TODO (ex3): Implement next-use algorithm on the CODE array.
        for (int i = CODE.size() - 1; i >= 0; i--)
        {
            MachineCodeLine currentNode = CODE.get(i);
            if (i < (CODE.size() - 1))
            {
                currentNode.Next_OUT = CODE.get(i + 1).Next_IN;
            }

            for (Map.Entry<String, ArrayList<Integer>> entry : currentNode.Next_OUT.nextUse.entrySet())
            {
                String v = entry.getKey();
                ArrayList<Integer> n = entry.getValue();

                if (!currentNode.DEF.contains(v))
                {
                    for (Integer number : n)
                    {
                        currentNode.Next_IN.add(v, number);
                    }
                }
            }

            for (String ref : currentNode.REF)
            {
                currentNode.Next_IN.add(ref, i);
            }
        }
    }

    String getLargestNextUse(NextUse next)
    {
        int largestNextUse = 0;
        String largestNextUseVariable = null;

        for (Map.Entry<String, ArrayList<Integer>> entry : next.nextUse.entrySet())
        {
            if (entry.getValue().get(0) > largestNextUse)
            {
                if (!this.REGISTERS.contains(entry.getKey()))
                    continue;

                largestNextUse = entry.getValue().get(0);
                largestNextUseVariable = entry.getKey();
            }
        }

        return largestNextUseVariable;
    }

    String getDeadVariable(Set<String> life)
    {
        for (String var : this.REGISTERS)
        {
            if (!life.contains(var))
            {
                return var;
            }
        }

        return null;
    }

    /**
     * This function should generate the LD and ST when needed.
     */
    public String chooseRegister(String variable, HashSet<String> life, NextUse next, boolean loadIfNotFound)
    {
        // TODO (ex4): if variable is a constant (starts with '#'), return variable
        // TODO (ex4): if REGISTERS contains variable, return "R" + index
        // TODO (ex4): if REGISTERS size is not max (< MAX_REGISTERS_COUNT), add variable to REGISTERS and return "R" + index
        // TODO (ex4): if REGISTERS has max size:
        // - put variable in space of an other variable which is not used anymore
        // *or*
        // - put variable in space of variable which has the largest next-use

        if (!loadIfNotFound)
        {
            this.MODIFIED.add(variable);
        }

        if (variable.startsWith("#"))
        {
            return variable;
        }

        if (this.REGISTERS.contains(variable))
        {
            return "R" + Integer.toString(this.REGISTERS.indexOf(variable));
        }

        if (this.REGISTERS.size() < this.MAX_REGISTERS_COUNT)
        {
            if (loadIfNotFound)
            {
                m_writer.println("LD " + "R" + Integer.toString(this.REGISTERS.size()) + ", " + variable);
            }
            this.REGISTERS.add(variable);
            return "R" + Integer.toString(this.REGISTERS.size() - 1);
        }

        // Registers are full...

        Set<String> variablesThatNeedToBeUsedLater = next.nextUse.keySet();

        String variableToReplace = getDeadVariable(variablesThatNeedToBeUsedLater);

        if (variableToReplace == null)
            variableToReplace = getLargestNextUse(next);

        if (variableToReplace != null)
        {

            int variableToReplaceIndex = this.REGISTERS.indexOf(variableToReplace);

            // Check if variable to replace has been modified
            if (this.MODIFIED.contains(variableToReplace) && life.contains(variableToReplace))
            {
                m_writer.println("ST " + variableToReplace + ", " + "R" + variableToReplaceIndex);

                this.MODIFIED.remove(variableToReplace);
            }

            this.REGISTERS.set(variableToReplaceIndex, variable);
            if (loadIfNotFound)
                m_writer.println("LD " + "R" + variableToReplaceIndex + ", " + variable);

            return "R" + Integer.toString(variableToReplaceIndex);
        }

        return null;
    }

    /**
     * Print the machine code in the output file
     */
    public void printMachineCode()
    {
        // TODO (ex4): Print the machine code in the output file.
        // You should change the code below.
        for (int i = 0; i < CODE.size(); i++)
        {
            m_writer.println("// Step " + i);

            MachineCodeLine currentNode = this.CODE.get(i);
            String op = currentNode.OPERATION;
            String assign = currentNode.ASSIGN;
            String left = currentNode.LEFT;
            String right = currentNode.RIGHT;


            String leftRegister = chooseRegister(left, currentNode.Life_IN, currentNode.Next_IN, true);
            String rightRegister = chooseRegister(right, currentNode.Life_IN, currentNode.Next_IN, true);

            String assignRegister = chooseRegister(assign, currentNode.Life_OUT, currentNode.Next_OUT, false);

            if (op != null)
                m_writer.println(op + " " + assignRegister + ", " + leftRegister + ", " + rightRegister);

            m_writer.println(CODE.get(i));
        }

        ArrayList<String> temp = (ArrayList<String>) this.RETURNS.clone();
        for (int i = this.CODE.size() - 1; i >= 0; i--)
        {
            for (String key : this.CODE.get(i).DEF)
            {
                if (temp.contains(key) && !this.CODE.get(i).Next_OUT.nextUse.containsKey(key) && this.MODIFIED.contains(key))
                {
                    m_writer.println("ST " + key + ", R" + Integer.toString(this.REGISTERS.indexOf(key)));
                }
            }
        }

    }

    /**
     * Order a set in alphabetic order
     *
     * @param set The set to order
     * @return The ordered list
     */
    public List<String> orderedSet(Set<String> set)
    {
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }

    /**
     * A class to store and manage next uses.
     */
    private class NextUse
    {
        public HashMap<String, ArrayList<Integer>> nextUse = new HashMap<>();

        public NextUse()
        {
        }

        public NextUse(HashMap<String, ArrayList<Integer>> nextUse)
        {
            this.nextUse = nextUse;
        }

        public ArrayList<Integer> get(String s)
        {
            return nextUse.get(s);
        }

        public void add(String s, int i)
        {
            if (!nextUse.containsKey(s))
            {
                nextUse.put(s, new ArrayList<>());
            }
            nextUse.get(s).add(i);
        }

        public String toString()
        {
            ArrayList<String> items = new ArrayList<>();
            for (String key : orderedSet(nextUse.keySet()))
            {
                Collections.sort(nextUse.get(key));
                items.add(String.format("%s:%s", key, nextUse.get(key)));
            }
            return String.join(", ", items);
        }

        @Override
        public Object clone()
        {
            return new NextUse((HashMap<String, ArrayList<Integer>>) nextUse.clone());
        }
    }

    /**
     * A struct to store the data of a machine code line.
     */
    private class MachineCodeLine
    {
        String OPERATION;
        String ASSIGN;
        String LEFT;
        String RIGHT;

        public HashSet<String> REF = new HashSet<>();
        public HashSet<String> DEF = new HashSet<>();

        public HashSet<String> Life_IN = new HashSet<>();
        public HashSet<String> Life_OUT = new HashSet<>();

        public NextUse Next_IN = new NextUse();
        public NextUse Next_OUT = new NextUse();

        public MachineCodeLine(String operation, String assign, String left, String right)
        {
            this.OPERATION = OPERATIONS.get(operation);
            this.ASSIGN = assign;
            this.LEFT = left;
            this.RIGHT = right;

            DEF.add(this.ASSIGN);
            if (this.LEFT.charAt(0) != '#')
                REF.add(this.LEFT);
            if (this.RIGHT.charAt(0) != '#')
                REF.add(this.RIGHT);
        }

        @Override
        public String toString()
        {
            String buffer = "";
            buffer += String.format("// Life_IN  : %s\n", Life_IN);
            buffer += String.format("// Life_OUT : %s\n", Life_OUT);
            buffer += String.format("// Next_IN  : %s\n", Next_IN);
            buffer += String.format("// Next_OUT : %s\n", Next_OUT);
            return buffer;
        }
    }
}
