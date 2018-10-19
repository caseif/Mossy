/*
 * This file is a part of Mossy.
 * Copyright (c) 2018, Max Roncace <mproncace@gmail.com>
 *
 * The MIT License
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

package net.caseif.mossy.assembly;

import net.caseif.moslib.AddressingMode;
import net.caseif.moslib.Instruction;
import net.caseif.moslib.Mnemonic;
import net.caseif.mossy.assembly.model.Statement;
import net.caseif.mossy.assembly.model.Token;
import net.caseif.mossy.assembly.parser.AssemblyLexer;
import net.caseif.mossy.assembly.parser.AssemblyParser;
import net.caseif.mossy.util.exception.LexerException;
import net.caseif.mossy.util.exception.ParserException;

import com.google.common.base.Preconditions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProgramAssembler {

    private List<Statement> statements;

    public void read(InputStream input) throws IOException, LexerException, ParserException {
        System.out.println("Lexing assembly...");

        List<Token> tokenized = AssemblyLexer.lex(input);

        System.out.println("Parsing assembly...");

        statements = AssemblyParser.parse(tokenized);
    }

    public void assemble(OutputStream output) throws IOException, ParserException {
        Preconditions.checkState(statements != null, "No program loaded.");

        Map<String, Integer> labelDict = buildLabelDictionary();

        byte[] bytecode = generateBytecode(labelDict);

        output.write(bytecode);

        output.close();
    }

    private byte[] generateBytecode(Map<String, Integer> labelDict) throws ParserException {
        ByteArrayOutputStream intermediate = new ByteArrayOutputStream();

        final int OFFSET = 0x8000; //TODO: read this from a .org directive

        int curOffset = 0;

        for (Statement stmt : statements) {
            switch (stmt.getType()) {
                case INSTRUCTION: {
                    Statement.InstructionStatement instrStmt = (Statement.InstructionStatement) stmt;

                    Optional<Instruction> instrOpt = Instruction.lookup(instrStmt.getMnemonic(), instrStmt.getAddressingMode());

                    if (!instrOpt.isPresent()) {
                        throw new ParserException(String.format(
                                "Instruction %s cannot be used with addressing mode %s.",
                                instrStmt.getMnemonic(), instrStmt.getAddressingMode()
                        ), instrStmt.getLine());
                    }

                    intermediate.write((byte) instrOpt.get().getOpcode());

                    curOffset += 1;

                    int operand;

                    if (instrStmt.getLabelRef().isPresent()) {
                        int realTarget = labelDict.get(instrStmt.getLabelRef().get());

                        if (instrStmt.getAddressingMode() == AddressingMode.REL) {
                            // the offset is relative to the address following the current instruction
                            // we've already incremented the offset by 1 for the opcode, and the
                            // addressing mode is always relative (1 byte operand), so we can just
                            // add 1 to the current offset to move it past the operand.
                            operand = realTarget - (curOffset + 1);
                        } else {
                            operand = realTarget;
                        }
                    } else if (instrStmt.getAddressingMode() != AddressingMode.IMP) {
                        operand = instrStmt.getOperand();
                    } else {
                        operand = -1;
                    }

                    if (instrStmt.getAddressingMode() != AddressingMode.IMP) {
                        // we only need to adjust the operand to account for the offset if
                        // we're jumping to an absolute address. we should keep it intact
                        // if we're using an indirect value.

                        if (instrStmt.getMnemonic().getType() == Mnemonic.Type.JUMP
                                && instrStmt.getAddressingMode() == AddressingMode.ABS) {
                            operand += OFFSET;
                        }

                        switch (instrStmt.getAddressingMode().getLength() - 1) {
                            case 1:
                                intermediate.write((byte) operand);

                                break;
                            case 2:
                                intermediate.write((byte) (operand & 0xFF)); // write LSB
                                intermediate.write((byte) ((operand >> 8) & 0xFF)); // write MSB

                                break;
                            default: // all 6502 addressing modes take either a word or a dword
                                throw new AssertionError("Unhandled case " + (instrStmt.getAddressingMode().getLength() - 1));
                        }

                        curOffset += instrStmt.getAddressingMode().getLength() - 1;
                    }

                    break;
                }
                // skip label definitions since we already built a dictionary
                case LABEL_DEF:
                case COMMENT:
                    continue;
                default:
                    throw new AssertionError("Unhandled case " + stmt.getType().name());
            }
        }

        return intermediate.toByteArray();
    }

    // helper method for reading and indexing all label definitions
    private Map<String, Integer> buildLabelDictionary() throws ParserException {
        Map<String, Integer> labelDict = new HashMap<>();

        int pc = 0;

        // first pass, for building the label dictionary
        for (Statement stmt : statements) {
            switch (stmt.getType()) {
                case INSTRUCTION: {
                    Statement.InstructionStatement instrStmt = (Statement.InstructionStatement) stmt;

                    // just increment the program counter appropriately
                    pc += instrStmt.getAddressingMode().getLength();

                    break;
                }
                case LABEL_DEF: {
                    Statement.LabelDefinitionStatement lblStmt = (Statement.LabelDefinitionStatement) stmt;

                    if (labelDict.containsKey(lblStmt.getId())) {
                        throw new ParserException("Found duplicate label " + lblStmt.getId() + "!", lblStmt.getLine());
                    }

                    // add the label to the dictionary - no need to increment the PC
                    labelDict.put(lblStmt.getId(), pc);

                    break;
                }
                case COMMENT: {
                    continue;
                }
                default: {
                    throw new AssertionError("Unhandled case " + stmt.getType().name());
                }
            }
        }

        return labelDict;
    }

}
