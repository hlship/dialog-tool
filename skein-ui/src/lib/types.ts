// This is used for the wire form and the in-memory form

export type KnotData = {
    id: number,
    parent_id: number | null,
    // Category is derived from response and unblessed
    category: Category,
    selected: number | null,
    label?: string,
    command: string,
    // When a command is first added, response is undefined, and unblessed is a string
    // In most cases, response is a string
    response?: string,
    unblessed?: string,
    children: number[];
}

export type Category = "ok" | "new" | "error";

export interface KnotChild {
    id: number,
    label: string,
    treeCategory: Category
}

export interface KnotNode  {
    id: number,
    data: KnotData,
    // Category inherited up from children:
    treeCategory: Category,
    children: KnotChild[]
}