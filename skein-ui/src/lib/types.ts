// This is used for the wire form and the in-memory form

export type KnotData = {
    id: number,
    parent_id: number | null,
    label?: string,
    command: string,
    // When a command is first added, response is undefined, and unblessed is a string
    // In most cases, response is a string
    response?: string,
    unblessed?: string,
    children: number[];
}

// OK: response, no unblessed
// NEW: unblessed, no response
// ERROR: both response and unblessed, and they are not equal

export enum Category { OK, NEW, ERROR }

export interface KnotChild {
    id: number,
    label: string,
    treeCategory: Category
}

export interface KnotNode  {
    id: number,
    data: KnotData,
    // Category for self:
    category: Category,
    // Category inherited up from children:
    treeCategory: Category,
    // Id this still needed?
    selectedChildId: number | null,
    children: KnotChild[]
}