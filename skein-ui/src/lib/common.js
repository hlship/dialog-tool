const url = "//localhost:10140/api";

export async function load() {
    const response = await fetch("//localhost:10140/api");
    
    return await response.json();
}

export async function postApi(payload) {
    const response = await fetch("//localhost:10140/api", {
        method: "POST",
        cache: "no-cache",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
    });

   return await response.json();

}