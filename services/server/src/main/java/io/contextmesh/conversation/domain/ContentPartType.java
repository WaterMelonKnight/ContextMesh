package io.contextmesh.conversation.domain;

/** Types reserved by the normalized contract. Generic JSON v1 accepts only {@link #TEXT}. */
public enum ContentPartType {
    TEXT, CODE, IMAGE_REF, FILE_REF, TOOL_CALL, TOOL_RESULT
}
